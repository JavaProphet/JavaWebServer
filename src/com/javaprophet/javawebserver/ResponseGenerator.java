package com.javaprophet.javawebserver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.json.simple.JSONObject;

public class ResponseGenerator {
	public ResponseGenerator() {
		
	}
	
	private static final SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
	
	public void process(RequestPacket request, ResponsePacket response) {
		if (!request.httpVersion.equals("HTTP/1.1")) {
			response.statusCode = 505;
			response.reasonPhrase = "NEEDS HTTP/1.1";
			response.httpVersion = request.httpVersion;
			return;
		}
		try {
			response.headers.addHeader("Date", sdf.format(new Date()));
			response.headers.addHeader("Server", "JWS/" + JavaWebServer.VERSION);
			if (request.method == Method.OPTIONS) {
				
			}else if (request.method == Method.GET) {
				Resource resource = getResource(request.target);
				if (resource == null || resource.data == null) {
					response.statusCode = 404;
					response.reasonPhrase = "Not Found";
					response.httpVersion = "HTTP/1.1";
					getErrorPage(response.body, request.target, 404, "Not Found", "The requested URL " + request.target + " was not found on this server.");
					return;
				}else {
					response.statusCode = 200;
					response.reasonPhrase = "OK";
					response.httpVersion = "HTTP/1.1";
					response.body.setBody(resource.data);
					response.body.setContentType(resource.type);
					return;
				}
				
			}else if (request.method == Method.HEAD) {
				
			}else if (request.method == Method.POST) {
				
			}else if (request.method == Method.PUT) {
				
			}else if (request.method == Method.DELETE) {
				
			}else if (request.method == Method.TRACE) {
				
			}else if (request.method == Method.CONNECT) {
				
			}
		}catch (Exception e) {
			e.printStackTrace();
			response.statusCode = 500;
			response.reasonPhrase = "Server Error";
			response.httpVersion = "HTTP/1.1";
			getErrorPage(response.body, request.target, 500, "Server Error", "The requested URL " + request.target + " caused a server failure.");
			return;
		}
	}
	
	public static final String crlf = System.getProperty("line.separator");
	
	public void getErrorPage(MessageBody body, String reqTarget, int statusCode, String reason, String info) {
		JSONObject errorPages = (JSONObject)JavaWebServer.mainConfig.get("errorpages");
		if (errorPages.containsKey(statusCode)) {
			try {
				String path = (String)errorPages.get(statusCode);
				Resource resource = getResource(path);
				if (resource != null) {
					body.setContentType(resource.type);
					if (resource.type.startsWith("text")) {
						String res = new String(resource.data);
						res = res.replace("$_statusCode_$", statusCode + "").replace("$_reason_$", reason).replace("$_info_$", info).replace("$_reqTarget_$", reqTarget);
						resource.data = res.getBytes();
					}
					body.setBody(resource.data);
					return;
				}
			}catch (Exception e) {
				e.printStackTrace();
			}
		}
		body.setContentType("text/html");
		body.setBody(("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">" + crlf + "<html><head>" + crlf + "<title>" + statusCode + " " + reason + "</title>" + crlf + "</head><body>" + crlf + "<h1>" + reason + "</h1>" + crlf + "<p>" + info + "</p>" + crlf + "</body></html>").getBytes());
		return;
	}
	
	public File getAbsolutePath(String reqTarget) {
		String rt = reqTarget;
		if (rt.contains("?")) {
			rt = rt.substring(0, rt.indexOf("?"));
		}
		File abs = new File(new File((String)JavaWebServer.mainConfig.get("htdocs")), rt);
		if (abs.isDirectory()) {
			abs = new File(abs, (String)JavaWebServer.mainConfig.get("index"));
		}
		return abs;
	}
	
	public Resource getResource(String reqTarget) throws IOException {
		try {
			File abs = getAbsolutePath(reqTarget);
			FileInputStream fin = new FileInputStream(abs);
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			int i = 1;
			byte[] buf = new byte[4096];
			while (i > 0) {
				i = fin.read(buf);
				if (i > 0) {
					bout.write(buf, 0, i);
				}
			}
			byte[] resource = bout.toByteArray();
			Resource r = new Resource(resource, Files.probeContentType(Paths.get(abs.getAbsolutePath())));
			return r;
		}catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
}
