package bormand.mp4download;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

public class CommandLine {

	private static void help() {
		System.out.println("java -jar mp4download.jar <url> [-o <filename>] [-s <size>]");
	}
	
	private static String extractFileName(URL url) throws UnsupportedEncodingException {
		String path = URLDecoder.decode(url.getPath(), "utf-8");
		int pos = path.lastIndexOf('/');
		if (pos != -1)
			path = path.substring(pos + 1);
		return path.trim();
	}
	
	public static void main(String[] args) throws IOException {

		if (args.length < 1) {
			help();
			return;
		}
			
		URL url = new URL(args[0]);
		String fileName = extractFileName(url);
		long size = 1000000000000000000l;
			
		for (int i=1; i<args.length; i++) {
			if (args[i].equals("-o")) {
				i++;
				if (i >= args.length) {
					help();
					return;
				}
				fileName = args[i];
			} else if (args[i].equals("-s")) {
				i++;
				if (i >= args.length) {
					help();
					return;
				}
				size = Long.valueOf(args[i]);
			} else {
				help();
				return;
			}
		}

		if ("".equals(fileName))
			throw new IllegalArgumentException("Destination file name must not be empty");
		
		System.out.println("url = " + url);
		System.out.println("fileName = " + fileName);
		System.out.println("size = " + size);
			
		MP4Downloader downloader = new MP4Downloader(url, fileName, size);
		downloader.download();
	}
	
}
