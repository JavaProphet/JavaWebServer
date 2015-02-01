package com.javaprophet.javawebserver.plugins.base;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.zip.CRC32;
import com.javaprophet.javawebserver.JavaWebServer;
import com.javaprophet.javawebserver.http.Method;
import com.javaprophet.javawebserver.http.StatusCode;
import com.javaprophet.javawebserver.networking.Packet;
import com.javaprophet.javawebserver.networking.packets.RequestPacket;
import com.javaprophet.javawebserver.networking.packets.ResponsePacket;
import com.javaprophet.javawebserver.plugins.Patch;

public class PatchETag extends Patch {
	
	public PatchETag(String name) {
		super(name);
		try {
			md5 = MessageDigest.getInstance("MD5");
		}catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void formatConfig(HashMap<String, Object> json) {
		
	}
	
	@Override
	public boolean shouldProcessPacket(Packet packet) {
		return false;
	}
	
	@Override
	public void processPacket(Packet packet) {
		
	}
	
	@Override
	public boolean shouldProcessResponse(ResponsePacket response, RequestPacket request, byte[] data) {
		return response.statusCode == 200 && (request.method == Method.GET || request.method == Method.HEAD) && response.body != null && data != null && data.length > 0;
	}
	
	public static MessageDigest md5 = null;
	
	@Override
	public byte[] processResponse(ResponsePacket response, RequestPacket request, byte[] data) {
		if (md5 == null) return data;
		CRC32 crc = new CRC32();
		crc.update(data);
		String etag = crc.getValue() + "";// bytesToHex(md5.digest(data));
		if (request.headers.hasHeader("If-None-Match")) {
			if (request.headers.getHeader("If-None-Match").replace("\"", "").equals(etag)) {
				JavaWebServer.rg.generateDefaultResponse(response, StatusCode.NOT_MODIFIED);
				response.body = null;
				return null;
			}
		}else {
			response.headers.addHeader("ETag", "\"" + etag + "\"");
		}
		return data;
	}
	
	@Override
	public void processMethod(RequestPacket request, ResponsePacket response) {
		
	}
	
}
