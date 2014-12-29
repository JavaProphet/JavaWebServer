package com.javaprophet.javawebserver.networking.packets;

import com.javaprophet.javawebserver.http.ContentEncoding;
import com.javaprophet.javawebserver.http.Header;
import com.javaprophet.javawebserver.http.Headers;
import com.javaprophet.javawebserver.http.MessageBody;
import com.javaprophet.javawebserver.networking.Packet;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class ResponsePacket extends Packet {
	public int statusCode = 200;
	public String reasonPhrase = "";
	public boolean isHead = false;
	
	public byte[] serialize(ContentEncoding ce) {
		try {
			Headers hc = headers.clone();
			ByteArrayOutputStream ser = new ByteArrayOutputStream();
			ser.write((httpVersion + " " + statusCode + " " + reasonPhrase + crlf).getBytes());
			if (body != null) {
				if (hc.hasHeader("Content-Length") && !isHead) {
					hc.getHeader("Content-Length").value = body.getBody().length + "";
				}else if (!hc.hasHeader("Transfer-Encoding") || !hc.getHeader("Transfer-Encoding").value.contains("chunked")) {
					hc.addHeader("Content-Length", body.getBody().length + ""); // TODO: chunked is incredibly broken
				}
				if (!hc.hasHeader("Content-Type")) {
					hc.addHeader("Content-Type", body.getContentType());
				}
			}
			byte[] finalc = new byte[0];
			if (ce == ContentEncoding.gzip || ce == ContentEncoding.xgzip) {
				ByteArrayOutputStream bout = new ByteArrayOutputStream();
				GZIPOutputStream gout = new GZIPOutputStream(bout);
				gout.write(body.getBody(), 0, body.getBody().length);
				gout.flush();
				gout.close();
				finalc = bout.toByteArray();
				if (hc.hasHeader("Content-Length")) {
					hc.getHeader("Content-Length").value = finalc.length + "";
				}
			}else if (ce == ContentEncoding.identity) {
				finalc = body.getBody();
			}
			if (ce != ContentEncoding.identity) hc.addHeader("Content-Encoding", ce.name);
			for (Header header : hc.getHeaders()) {
				ser.write((header.toLine() + crlf).getBytes());
			}
			ser.write(crlf.getBytes());
			ser.write(finalc);
			return ser.toByteArray();
		}catch (Exception e) {
			e.printStackTrace();
		}
		return new byte[0];
	}
	
	public String toString() {
		return new String(serialize(ContentEncoding.identity));
	}
	
	public String toString(ContentEncoding ce) {
		return new String(serialize(ce));
	}
	
	public void write(DataOutputStream out, ContentEncoding ce) throws IOException {
		out.write(serialize(ce));
		out.flush();
	}
	
	public static ResponsePacket read(DataInputStream in) throws IOException {
		ResponsePacket incomingResponse = new ResponsePacket();
		String statusLine = "";
		try {
			statusLine = in.readLine().trim();
		}catch (NullPointerException e) {
			return null;
		}
		int b = statusLine.indexOf(" ");
		incomingResponse.httpVersion = statusLine.substring(0, b);
		incomingResponse.statusCode = Integer.parseInt(statusLine.substring(b + 1, b = statusLine.indexOf(" ", b + 1)));
		incomingResponse.reasonPhrase = statusLine.substring(b + 1);
		Headers headers = incomingResponse.headers;
		while (true) {
			String headerLine = in.readLine().trim();
			if (headerLine.length() == 0) {
				break;
			}else {
				headers.addHeader(headerLine);
			}
		}
		boolean chunked = false;
		boolean htc = headers.hasHeader("Transfer-Encoding");
		boolean hcl = headers.hasHeader("Content-Length");
		if (htc) {
			String[] tenc = headers.getHeader("Transfer-Encoding").value.split(", ");
			if (tenc[tenc.length - 1].equals("chunked")) {
				chunked = true;
			}
		}
		byte[] bbody = new byte[0];
		if (chunked && htc) {
			Header te = headers.getHeader("Transfer-Encoding");
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			boolean lwl = false;
			int length = 1;
			while (length > 0) {
				if (lwl) {
					byte[] data = new byte[length];
					in.readFully(data);
					bout.write(data);
					lwl = false;
				}else {
					String len = in.readLine().trim();
					if (len.equals("")) {
						continue;
					}
					try {
						length = Integer.parseInt(len, 16);
					}catch (NumberFormatException e) {
						continue;
					}
					lwl = true;
				}
			}
			bbody = bout.toByteArray();
			if (te.value.equals("chunked")) {
				headers.removeHeaders("Transfer-Encoding");
			}else {
				String ntev = "";
				for (String sp : te.value.split(",")) {
					if (!sp.equals("chunked")) {
						ntev = sp + ", ";
					}
				}
				if (ntev.length() > 0) {
					ntev = ntev.substring(0, ntev.length() - 2);
				}
				te.value = ntev;
			}
			headers.addHeader("Content-Length", bbody.length + "");
		}else if (hcl) {
			bbody = new byte[Integer.parseInt(headers.getHeader("Content-Length").value)];
			byte[] buf = new byte[4096];
			int i = 1;
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			while (i > 0) {
				i = in.read(buf);
				if (i > 0) {
					bout.write(buf, 0, i);
				}
			}
			bbody = bout.toByteArray();
		}
		MessageBody body = new MessageBody(incomingResponse, bbody);
		incomingResponse.body = body;
		return incomingResponse;
	}
}