package org.avuna.httpd.http.networking;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import org.avuna.httpd.AvunaHTTPD;
import org.avuna.httpd.http.Method;
import org.avuna.httpd.http.Resource;
import org.avuna.httpd.http.plugins.javaloader.JavaLoaderStream;
import org.avuna.httpd.util.Logger;

/**
 * This is for responeses to the client.
 */
public class ResponsePacket extends Packet {
	public int statusCode = 200;
	public String reasonPhrase = "";
	public RequestPacket request;
	public JavaLoaderStream reqStream = null;
	public boolean done = false;
	public DataInputStream toStream = null;
	
	public byte[] serialize() {
		return serialize(true, true);
	}
	
	// public ResponsePacket clone() {
	// ResponsePacket n = new ResponsePacket();
	// n.statusCode = statusCode;
	// n.reasonPhrase = reasonPhrase;
	// n.request = request;
	// n.httpVersion = httpVersion;
	// n.headers = headers.clone();
	// n.body = body.clone();
	// n.reqTransfer = reqTransfer;
	// return n;
	// }
	
	public byte[] cachedSerialize = null;
	public ResponsePacket cachedPacket = null;
	
	public byte[] serialize(boolean data, boolean head) {
		try {
			byte[] finalc = this.body == null ? null : this.body.data;
			finalc = request.host.getHost().patchBus.processResponse(this, this.request, finalc);
			if (this.drop) {
				return null;
			}
			StringBuilder ser = new StringBuilder();
			if (head) {
				ser.append((this.httpVersion + " " + this.statusCode + " " + this.reasonPhrase + AvunaHTTPD.crlf));
				HashMap<String, ArrayList<String>> hdrs = this.headers.getHeaders();
				for (String key : hdrs.keySet()) {
					for (String val : hdrs.get(key)) {
						ser.append((key + ": " + val + AvunaHTTPD.crlf));
					}
				}
				ser.append(AvunaHTTPD.crlf);
			}
			cachedSerialize = ser.toString().getBytes();
			byte[] add = null;
			if (data && finalc != null) {
				if (!this.headers.hasHeader("Transfer-Encoding")) {
					add = finalc;
				}else {
					// cachedPacket = this;
					cachedSerialize = new byte[0];
					return new byte[0];
				}
				this.body = new Resource(finalc, this.headers.hasHeader("Content-Type") ? this.headers.getHeader("Content-Type") : "text/html", this.request.target, this.body.effectiveOverride);
			}else {
				add = new byte[0];// AvunaHTTPD.crlf.getBytes();
			}
			byte[] total = new byte[cachedSerialize.length + add.length];
			System.arraycopy(cachedSerialize, 0, total, 0, cachedSerialize.length);
			System.arraycopy(add, 0, total, cachedSerialize.length, add.length);
			return total;
		}catch (Exception e) {
			Logger.logError(e);
		}
		cachedSerialize = new byte[0];
		return new byte[0];
	}
	
	public String toString() {
		return new String(cachedSerialize);
	}
	
	public long bwt = 0L;
	public boolean reqTransfer = false;
	
	public byte[] subwrite = null;
	public boolean validSub = true;
	public boolean close = false;
	
	public void prewrite() {
		subwrite = serialize(request.method != Method.HEAD, true);
		if (this.headers.hasHeader("Transfer-Encoding")) {
			String te = this.headers.getHeader("Transfer-Encoding");
			if (te.equals("chunked")) {
				subwrite = null;
				validSub = false;
			}
		}
		this.bwt = System.nanoTime();
		if (this.headers.hasHeader("Transfer-Encoding")) {
			String te = this.headers.getHeader("Transfer-Encoding");
			if (te.equals("chunked")) {
				this.reqTransfer = true;
			}
		}
		if (headers.hasHeader("Connection")) {
			String c = headers.getHeader("Connection");
			if (c.equals("Close")) {
				close = true;
			}
		}
	}
	
	public void subwrite() {
		subwrite = serialize(true, false);
		if (this.headers.hasHeader("Transfer-Encoding")) {
			String te = this.headers.getHeader("Transfer-Encoding");
			if (te.equals("chunked")) {
				subwrite = null;
				validSub = false;
			}
		}
		this.bwt = System.nanoTime();
	}
	
	public void write(DataOutputStream out, boolean deprecated) throws IOException {
		byte[] write = serialize(request.method != Method.HEAD, true);
		this.bwt = System.nanoTime();
		if (write == null) {
			return;
		}else if (write.length == 0) {
			
		}else {
			out.write(write);
			write = null;
			out.flush();
		}
	}
}
