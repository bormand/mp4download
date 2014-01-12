package bormand.mp4download;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

public class MP4BoxHeader {

	private final long size;
	private final byte[] uuid;
	private final String type;
	private final static Charset ascii = Charset.forName("US-ASCII");

	public MP4BoxHeader(long size, byte[] uuid) {
		if (uuid.length != 16)
			throw new IllegalArgumentException("uuid must be 16 bytes long");
		this.size = size;
		this.type = "uuid";
		this.uuid = uuid.clone();
	}

	public MP4BoxHeader(long size, String type) {
		if (type.length() != 4)
			throw new IllegalArgumentException("type must be 4 letters long");
		this.size = size;
		this.type = type;
		this.uuid = null;
	}

	public static MP4BoxHeader parse(ByteBuffer buf) throws IOException {
		buf.order(ByteOrder.BIG_ENDIAN);
		long size = buf.getInt();
		if (size < 0)
			size += 0x100000000l;
		byte[] btype = new byte[4];
		buf.get(btype);
		for (int i=0; i<4; i++)
			if (!(btype[i] >= 'a' && btype[i] <= 'z' || btype[i] >= 'A' && btype[i] <= 'Z' || btype[i] >= '0' && btype[i] <= '9'))
				throw new IOException("Wrong characters in box type.");
		String type = new String(btype, ascii);
		
		if (size == 1)
			size = buf.getLong();
		
		if (type == "uuid") {
			byte[] uuid = new byte[16];
			buf.get(uuid);
			return new MP4BoxHeader(size, uuid);
		} else {
			return new MP4BoxHeader(size, type);
		}
	}

	public long getSize() {
		return size;
	}

	public byte[] getUuid() {
		return uuid;
	}

	public String getType() {
		return type;
	}
	
}
