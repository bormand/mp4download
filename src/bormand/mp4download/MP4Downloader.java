package bormand.mp4download;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class MP4Downloader {

	private static int TRANSFER_BUFFER_SIZE = 16 * 1024 * 1024;
	private static int MOOV_BOX_SIZE_LIMIT = 50 * 1024 * 1024; 
	private static int MOOV_BOX_STEP = 2 * 1024 * 1024; 
	
	private String fileName;
	private long limit;
	private HttpRandomAccessReader reader;

	public MP4Downloader(URL url, String fileName, long limit) {
		this.reader = new HttpRandomAccessReader(url);
		this.fileName = fileName;
		this.limit = limit;
	}

	private ByteBuffer downloadSmallPart(long from, int count) throws IOException {
		ByteBuffer buf = ByteBuffer.allocate(count);
		int offset = 0;
		try (InputStream stream = reader.openStream(from, count)) {
			while (count > 0) {
				int r = stream.read(buf.array(), offset, count);
				if (r == -1)
					throw new IOException("End of stream");
				offset += r;
				count -= r;
			}
		}
		return buf;
	}
	
	private void downloadContent(FileChannel output, long offset, long skipAtEnd) throws IOException {
		System.out.println("Starting data transfer...");

		long size = reader.getSize();
		if (size > limit)
			size = limit;
		size -= offset + skipAtEnd;
		InputStream stream = reader.openStream(offset, size);
		
		ByteBuffer buf = ByteBuffer.allocate(TRANSFER_BUFFER_SIZE);
		long total = 0;
		while (total < size) {
			buf.clear();
			int r = stream.read(buf.array());
			if (r == -1)
				throw new IOException("Unexpected end of file");
			buf.position(r);
			buf.flip();
			output.write(buf);
			total += r;
			System.out.println(total + "/" + size);
		}
		System.out.println("Data transfer complete");
	}

	private ByteBuffer extractMoovBoxFromTail() throws IOException {
		ByteBuffer buf = ByteBuffer.allocate(MOOV_BOX_SIZE_LIMIT);
		int total = 0;
		while (total + MOOV_BOX_STEP <= MOOV_BOX_SIZE_LIMIT) {
			// fetch next part
			ByteBuffer part = downloadSmallPart(reader.getSize() - total - MOOV_BOX_STEP, MOOV_BOX_STEP);
			total += MOOV_BOX_STEP;
			buf.position(MOOV_BOX_SIZE_LIMIT - total);
			buf.put(part);
			
			// and try to locate 'moov'
			int begin = MOOV_BOX_SIZE_LIMIT - total + MOOV_BOX_STEP + 7;
			if (begin >= MOOV_BOX_SIZE_LIMIT)
				begin = MOOV_BOX_SIZE_LIMIT - 1;
			int end = MOOV_BOX_SIZE_LIMIT - total + 7;
			for (int i=begin; i>end; i--) {
				if (buf.get(i) == 'v' && buf.get(i-1) == 'o' && buf.get(i-2) == 'o' &&
						buf.get(i-3) == 'm' && buf.getInt(i-7) == MOOV_BOX_SIZE_LIMIT - i + 7) {
					buf.limit(MOOV_BOX_SIZE_LIMIT);
					buf.position(i - 7);
					buf.compact();
					buf.flip();
					return buf;
				}
			}
		}

		throw new IOException("Can't locate 'moov' block within " + MOOV_BOX_SIZE_LIMIT + " bytes from EOF");
	}

	private void fixupOffsetsInStco(ByteBuffer buf, int offset) throws IOException {
		System.out.println("        'stco' found");
		buf.order(ByteOrder.BIG_ENDIAN);
		int entries = buf.getInt(4);
		System.out.println(entries + " entries" + buf.limit());
		for (int i=0; i<entries; i++) {
			int value = buf.getInt(8 + 4 * i);
			buf.putInt(8 + 4 * i, value + offset);
		}
	}

	private void fixupOffsetsInCo64(ByteBuffer buf, int offset) throws IOException {
		System.out.println("        'co64' found");
		buf.order(ByteOrder.BIG_ENDIAN);
		int entries = buf.getInt(4);
		System.out.println(entries + " entries" + buf.limit());
		for (int i=0; i<entries; i++) {
			long value = buf.getLong(8 + 8 * i);
			buf.putLong(8 + 8 * i, value + offset);
		}
	}

	private void fixupOffsetsInStbl(ByteBuffer buf, int offset) throws IOException {
		System.out.println("      'stbl' found");

		while (buf.remaining() > 0) {
			int start = buf.position();
			MP4BoxHeader header = MP4BoxHeader.parse(buf);
			if (header.getType().equals("stco")) {
				ByteBuffer stco = buf.slice();
				stco.limit((int)(header.getSize() - buf.position() + start));
				fixupOffsetsInStco(stco, offset);
			} else if (header.getType().equals("co64")) {
				ByteBuffer stco = buf.slice();
				stco.limit((int)(header.getSize() - buf.position() + start));
				fixupOffsetsInCo64(stco, offset);
			}
			buf.position((int)(start + header.getSize()));
		}
	}

	private void fixupOffsetsInMinf(ByteBuffer buf, int offset) throws IOException {
		System.out.println("    'minf' found");

		while (buf.remaining() > 0) {
			int start = buf.position();
			MP4BoxHeader header = MP4BoxHeader.parse(buf);
			if (header.getType().equals("stbl")) {
				ByteBuffer stbl = buf.slice();
				stbl.limit((int)(header.getSize() - buf.position() + start));
				fixupOffsetsInStbl(stbl, offset);
			}
			buf.position((int)(start + header.getSize()));
		}
	}

	private void fixupOffsetsInMdia(ByteBuffer buf, int offset) throws IOException {
		System.out.println("  'mdia' found");

		while (buf.remaining() > 0) {
			int start = buf.position();
			MP4BoxHeader header = MP4BoxHeader.parse(buf);
			if (header.getType().equals("minf")) {
				ByteBuffer minf = buf.slice();
				minf.limit((int)(header.getSize() - buf.position() + start));
				fixupOffsetsInMinf(minf, offset);
			}
			buf.position((int)(start + header.getSize()));
		}
	}

	private void fixupOffsetsInTrack(ByteBuffer buf, int offset) throws IOException {
		System.out.println("'trac' found");
		
		while (buf.remaining() > 0) {
			int start = buf.position();
			MP4BoxHeader header = MP4BoxHeader.parse(buf);
			if (header.getType().equals("mdia")) {
				ByteBuffer mdia = buf.slice();
				mdia.limit((int)(header.getSize() - buf.position() + start));
				fixupOffsetsInMdia(mdia, offset);
			}
			buf.position((int)(start + header.getSize()));
		}
	}

	private void fixupOffsets(ByteBuffer buf, int offset) throws IOException {
		MP4BoxHeader moov = MP4BoxHeader.parse(buf);
		if (!moov.getType().equals("moov"))
			throw new IOException("WTF!?");
		
		while (buf.position() < moov.getSize()) {
			int start = buf.position();
			MP4BoxHeader header = MP4BoxHeader.parse(buf);
			if (header.getType().equals("trak")) {
				ByteBuffer trak = buf.slice();
				trak.limit((int)(header.getSize() - buf.position() + start));
				fixupOffsetsInTrack(trak, offset);
			}
			buf.position((int)(start + header.getSize()));
		}
	}

	public void download() throws IOException {
		try (FileChannel output = FileChannel.open(Paths.get(fileName),
				StandardOpenOption.WRITE, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING)) {

			// check for 'ftyp' header
			ByteBuffer buf = downloadSmallPart(0, 1024);
			MP4BoxHeader ftypHeader = MP4BoxHeader.parse(buf);
			if (!ftypHeader.getType().equals("ftyp"))
				throw new IOException("MP4 file doesn's start with ftyp box");
			buf.position((int)ftypHeader.getSize());
			
			// 	read next header and check for 'moov'
			MP4BoxHeader header = MP4BoxHeader.parse(buf);
			if (header.getType().equals("moov")) {
				System.out.println("'moov' box detected at start of file, starting direct download");
				downloadContent(output, 0, 0);
				return;
			}

			// try to locate 'moov' box at end of file
			System.out.println("Trying to locate 'moov' box at end of file");
			ByteBuffer moov = extractMoovBoxFromTail();

			System.out.println("'moov' box detected at " + moov.limit() + " bytes from EOF");

			// fixup moov box
			System.out.println("fixing offsets in 'moov' box...");
			fixupOffsets(moov, moov.limit());
			
			// write ftyp box, than moov box
			System.out.println("Writing headers...");
			buf.position(0);
			buf.limit((int)ftypHeader.getSize());
			output.write(buf);
			moov.position(0);
			output.write(moov);

			// start content downloading
			downloadContent(output, ftypHeader.getSize(), moov.limit());
		}
	}

}
