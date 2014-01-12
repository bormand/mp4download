package bormand.mp4download;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map.Entry;

public class HttpRandomAccessReader {

	private URL url;
	private boolean prepared = false;
	private String ifRange;
	private long size;

	public HttpRandomAccessReader(URL url) {
		this.url = url;
	}
	
	private void prepare() throws IOException {
		if (prepared)
			return;
		
		System.out.println("Sending HEAD request to " + url);
		HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		conn.setRequestMethod("HEAD");
		conn.connect();
		
		System.out.println("Got HTTP headers:");
		for (Entry<String, List<String>> entry: conn.getHeaderFields().entrySet()) {
			System.out.println("  " + entry.getKey() + " = " + entry.getValue());
		}
		
		if (conn.getResponseCode() != HttpURLConnection.HTTP_OK)
			throw new IOException("HTTP status code is not 200 OK");
		
		if (!conn.getHeaderField("Accept-Ranges").equals("bytes"))
			throw new IOException("No Accept-Ranges=bytes in server response");
		
		size = conn.getHeaderFieldLong("Content-Length", -1);
		if (size == -1)
			throw new IOException("No Content-Length field in server response");
		
		ifRange = conn.getHeaderField("ETag");
		if (ifRange == null)
			ifRange = conn.getHeaderField("Last-Modified");
		if (ifRange == null)
			throw new IOException("No ETag or Last-Modified fields in server response");
		
		prepared = true;
	}

	public long getSize() throws IOException {
		prepare();
		return size; 
	}

	public InputStream openStream(long from, long count) throws IOException {
		prepare();
		
		System.out.println("Sending GET request to " + url + " from " + from + " with size " + count);
		HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		conn.addRequestProperty("If-Range", ifRange);
		conn.addRequestProperty("Range", "bytes=" + from + "-" + (from + count -1));
		conn.connect();
		System.out.println("Got HTTP headers:");
		for (Entry<String, List<String>> entry: conn.getHeaderFields().entrySet()) {
			System.out.println("  " + entry.getKey() + " = " + entry.getValue());
		}
		if (conn.getResponseCode() != HttpURLConnection.HTTP_PARTIAL)
			throw new IOException("HTTP status code is not 206 Partial Content");
		if (!conn.getHeaderField("Content-Range").equals("bytes " + from + "-" + (from + count - 1) + "/" + size))
			throw new IOException("Wrong range in server response");
		return conn.getInputStream();
	}
	
}
