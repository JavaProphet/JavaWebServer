package com.javaprophet.javawebserver.util;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.javaprophet.javawebserver.JavaWebServer;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Config {
	private final File cfg;
	private JSONObject json = new JSONObject();
	
	public Config(File cfg) throws IOException {
		this.cfg = cfg;
		if (!cfg.exists()) {
			save();
		}
	}
	
	public Object get(String name) {
		return json.get(name);
	}
	
	public void set(String name, String value) {
		json.put(name, value);
	}
	
	private void format() {
		if (!json.containsKey("version")) json.put("version", JavaWebServer.VERSION);
		if (!json.containsKey("htdocs")) json.put("htdocs", "C:\\jhtdocs");
		if (!json.containsKey("bindport")) json.put("bindport", 80);
		if (!json.containsKey("errorpages")) json.put("errorpages", new JSONObject());
		if (!json.containsKey("index")) json.put("index", "index.html");
	}
	
	public void load() throws IOException, ParseException {
		if (!cfg.exists() && cfg.isFile()) {
			format();
			save();
		}
		DataInputStream in = new DataInputStream(new FileInputStream(cfg));
		byte[] buf = new byte[4096];
		int i = 1;
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		while (i > 0) {
			i = in.read(buf);
			if (i > 0) {
				bout.write(buf, 0, i);
			}
		}
		JSONParser parse = new JSONParser();
		json = (JSONObject)parse.parse(new String(bout.toByteArray()));
		format();
	}
	
	public void save() throws IOException {
		format();
		if (!cfg.exists() || !cfg.isFile()) {
			cfg.createNewFile();
		}
		DataOutputStream out = new DataOutputStream(new FileOutputStream(cfg));
		out.write(json.toJSONString().getBytes());
		out.flush();
		out.close();
	}
}