package bormand.mp4download;

import java.io.IOException;
import java.net.URL;

public class CommandLine {

	public static void main(String[] args) throws IOException {
		
		if (args.length < 3) {
			System.out.println("java -jar mp4download.jar <url> <filename> <size>");
			return;
		}
		
		MP4Downloader downloader = new MP4Downloader(new URL(args[0]), args[1], Long.valueOf(args[2]));
		downloader.download();
		
	}
	
}
