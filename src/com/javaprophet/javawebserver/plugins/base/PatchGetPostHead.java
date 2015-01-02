package com.javaprophet.javawebserver.plugins.base;

import java.io.File;
import org.json.simple.JSONObject;
import com.javaprophet.javawebserver.JavaWebServer;
import com.javaprophet.javawebserver.http.Method;
import com.javaprophet.javawebserver.http.Resource;
import com.javaprophet.javawebserver.http.ResponseGenerator;
import com.javaprophet.javawebserver.http.StatusCode;
import com.javaprophet.javawebserver.networking.Packet;
import com.javaprophet.javawebserver.networking.packets.RequestPacket;
import com.javaprophet.javawebserver.networking.packets.ResponsePacket;
import com.javaprophet.javawebserver.plugins.Patch;
import com.javaprophet.javawebserver.plugins.PatchRegistry;

public class PatchGetPostHead extends Patch {
	
	public PatchGetPostHead(String name) {
		super(name);
		PatchRegistry.registerMethod(Method.GET, this);
		PatchRegistry.registerMethod(Method.POST, this);
		PatchRegistry.registerMethod(Method.HEAD, this);
	}
	
	@Override
	public void formatConfig(JSONObject json) {
		
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
		return false;
	}
	
	@Override
	public byte[] processResponse(ResponsePacket response, RequestPacket request, byte[] data) {
		return data;
	}
	
	@Override
	public void processMethod(RequestPacket request, ResponsePacket response) {
		Resource resource = JavaWebServer.fileManager.getResource(request.target);
		if (resource == null || resource.data == null) {
			ResponseGenerator.generateDefaultResponse(response, StatusCode.NOT_FOUND);
			JavaWebServer.fileManager.getErrorPage(response.body, request.target, StatusCode.NOT_FOUND, "The requested URL " + request.target + " was not found on this server.");
			return;
		}else {
			File f = new File(JavaWebServer.fileManager.getHTDocs(), request.target);
			if (f.isDirectory() && !request.target.endsWith("/")) {
				ResponseGenerator.generateDefaultResponse(response, StatusCode.PERM_REDIRECT); // TODO: not relative
				response.headers.addHeader("Location", request.target + "/");
				response.headers.addHeader("Content-Length", "0");
				response.body.setBody(null);
			}else {
				ResponseGenerator.generateDefaultResponse(response, StatusCode.OK);
				response.body.setBody(resource);
			}
			return;
		}
	}
}
