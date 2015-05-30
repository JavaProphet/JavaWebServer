package org.avuna.httpd.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import org.avuna.httpd.AvunaHTTPD;
import org.avuna.httpd.hosts.Host;
import org.avuna.httpd.hosts.HostHTTP;
import org.avuna.httpd.http.Resource;
import org.avuna.httpd.http.StatusCode;
import org.avuna.httpd.http.networking.RequestPacket;
import org.avuna.httpd.http.plugins.Patch;
import org.avuna.httpd.http.plugins.base.PatchChunked;
import org.avuna.httpd.http.plugins.base.PatchGZip;
import org.avuna.httpd.http.plugins.base.PatchInline;
import org.avuna.httpd.http.plugins.javaloader.lib.HTMLCache;
import org.avuna.httpd.http.plugins.javaloader.lib.JavaLoaderUtil;
import org.avuna.httpd.http.util.OverrideConfig;

public class FileManager {
	public FileManager() {
		
	}
	
	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
	
	public String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
	
	private File dir = null, logs = null;
	private HashMap<String, File> plugin = new HashMap<String, File>();
	private HashMap<String, File> base = new HashMap<String, File>();
	private HashMap<HostHTTP, File> plugins = new HashMap<HostHTTP, File>();
	
	public File getMainDir() {
		return dir == null ? (dir = new File(AvunaHTTPD.mainConfig.getNode("dir").getValue())) : dir;
	}
	
	public File getPlugins(HostHTTP host) {
		if (plugins.containsKey(host)) {
			return plugins.get(host);
		}else {
			File f = new File(host.getConfig().getNode("plugins").getValue());
			plugins.put(host, f);
			return f;
		}
	}
	
	public File getLogs() {
		return logs == null ? (logs = new File(AvunaHTTPD.mainConfig.getNode("logs").getValue())) : logs;
	}
	
	public File getPlugin(Patch p) {
		if (!plugin.containsKey(p.hashCode() + "" + p.registry.host.hashCode())) {
			plugin.put(p.hashCode() + "" + p.registry.host.hashCode(), new File(getPlugins(p.registry.host), p.name));
		}
		return plugin.get(p.hashCode() + "" + p.registry.host.hashCode());
	}
	
	public File getBaseFile(String name) {
		if (!base.containsKey(name)) {
			base.put(name, new File(getMainDir(), name));
		}
		return base.get(name);
	}
	
	public void clearCache() throws IOException {
		HTMLCache.reloadAll();
		String[] delKeys = new String[cache.size()];
		int delSize = 0;
		for (String file : cache.keySet()) {
			if (!extCache.get(file).equals("application/x-java")) {
				delKeys[delSize++] = file;
			}
		}
		for (int i = 0; i < delSize; i++) {
			cache.remove(delKeys[i]);
			extCache.remove(delKeys[i]);
			lwiCache.remove(delKeys[i]);
			tbCache.remove(delKeys[i]);
			absCache.remove(delKeys[i]);
		}
		cConfigCache.clear();
		for (Host host : AvunaHTTPD.hosts.values()) {
			if (host instanceof HostHTTP) {
				PatchInline pi = ((PatchInline)((HostHTTP)host).registry.getPatchForClass(PatchInline.class));
				if (pi != null) pi.clearCache();
				PatchGZip pg = ((PatchGZip)((HostHTTP)host).registry.getPatchForClass(PatchGZip.class));
				if (pg != null) pg.clearCache();
			}
		}
		
	}
	
	public void flushjl() throws IOException {
		String[] delKeys = new String[cache.size()];
		int delSize = 0;
		for (String file : cache.keySet()) {
			if (extCache.get(file).equals("application/x-java")) {
				if (delSize > delKeys.length) {
					String[] ndk = new String[delKeys.length + 1];
					System.arraycopy(delKeys, 0, ndk, 0, delKeys.length);
					delKeys = ndk;
				}
				delKeys[delSize++] = file;
			}
		}
		for (int i = 0; i < delSize; i++) {
			cache.remove(delKeys[i]);
			extCache.remove(delKeys[i]);
			lwiCache.remove(delKeys[i]);
			tbCache.remove(delKeys[i]);
			absCache.remove(delKeys[i]);
		}
	}
	
	public Resource getErrorPage(RequestPacket request, String reqTarget, StatusCode status, String info) {
		ConfigNode errorPages = request.host.getHost().getConfig().getNode("errorpages");
		if (errorPages.containsNode(status.getStatus() + "")) {
			try {
				String path = errorPages.getNode(status.getStatus() + "").getValue();
				Resource resource = getResource(path, request);
				if (resource != null) {
					if (resource.type.startsWith("text")) {
						String res = new String(resource.data);
						res = res.replace("$_statusCode_$", status.getStatus() + "").replace("$_reason_$", status.getPhrase()).replace("$_info_$", JavaLoaderUtil.htmlescape(info)).replace("$_reqTarget_$", JavaLoaderUtil.htmlescape(reqTarget));
						resource.data = res.getBytes();
					}
					return resource;
				}
			}catch (Exception e) {
				Logger.logError(e);
			}
		}
		StringBuilder pb = new StringBuilder();
		pb.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">");
		pb.append("<html><head>");
		pb.append("<title>");
		pb.append(status.getStatus());
		pb.append(" ");
		pb.append(status.getPhrase());
		pb.append("</title>");
		pb.append("</head><body>");
		pb.append("<h1>");
		pb.append(status.getPhrase());
		pb.append("</h1>");
		pb.append("<p>");
		pb.append(JavaLoaderUtil.htmlescape(info));
		pb.append("</p>");
		pb.append("</body></html>");
		Resource error = new Resource(pb.toString().getBytes(), "text/html");
		return error;
	}
	
	private boolean lwi = false;// TODO: thread safety?
	
	public File getAbsolutePath(String reqTarget2, RequestPacket request) {
		String reqTarget = reqTarget2;
		lwi = false;
		File htd = request.host.getHTDocs();
		File abs = htd;
		String htds = htd.getAbsolutePath();
		String rdtf = null;
		if (request.rags1 != null && request.rags2 != null) {
			String subabs = reqTarget;
			String nsa = subabs.replaceAll(request.rags1, request.rags2); // TODO: edward snowden ;)
			if (!subabs.equals(nsa)) {
				File saf = new File(htd, subabs);
				File nsaf = new File(htd, nsa);
				String safs = saf.getAbsolutePath();
				String nsafs = nsaf.getAbsolutePath();
				if (!safs.startsWith(htds) || !nsafs.startsWith(htds)) {
					return null;
				}
				rdtf = safs;
				if (!saf.exists()) {
					reqTarget = nsa;
				}
			}
		}
		String[] t = new String[0];
		try {
			t = URLDecoder.decode(reqTarget, "UTF-8").split("/");
		}catch (UnsupportedEncodingException e) {
			Logger.logError(e);
		}
		boolean ext = false;
		String ep = "";
		for (String st : t) {
			if (ext) {
				ep += "/" + st;
			}else {
				abs = new File(abs, st);
				if (abs.isFile() || (rdtf != null && abs.getAbsolutePath().startsWith(rdtf))) {
					ext = true;
				}
			}
		}
		request.extraPath = ep;
		String abspr = abs.getAbsolutePath();
		if (!abspr.startsWith(htds)) {
			return null;
		}
		
		if (abs.isDirectory()) {
			String[] index = null;
			if (request.overrideIndex != null) {
				index = request.overrideIndex;
			}else {
				index = request.host.getHost().getConfig().getNode("index").getValue().split(",");
			}
			for (String i : index) {
				i = i.trim();
				if (i.startsWith("/")) {
					i = i.substring(1);
				}
				String abst = abs.toString().replace("\\", "/");
				if (!abst.endsWith("/")) {
					abst += "/";
				}
				File f = new File(abst + i);
				if (f.exists()) {
					abs = f;
					if (ep.length() == 0) lwi = true;
					break;
				}
			}
		}
		return abs;
	}
	
	public String correctForIndex(String reqTarget, RequestPacket request) {
		String p = getAbsolutePath(reqTarget, request).getAbsolutePath().replace("\\", "/");
		return p.substring(request.host.getHTDocs().getAbsolutePath().replace("\\", "/").length());
	}
	
	public static final HashMap<String, byte[]> cache = new HashMap<String, byte[]>();
	public static final HashMap<String, String> extCache = new HashMap<String, String>();
	public static final HashMap<String, String> absCache = new HashMap<String, String>();
	public static final HashMap<String, Boolean> lwiCache = new HashMap<String, Boolean>();
	public static final HashMap<String, Boolean> tbCache = new HashMap<String, Boolean>();
	public static final HashMap<String, OverrideConfig> cConfigCache = new HashMap<String, OverrideConfig>();
	private static long cacheClock = 0L;
	
	public OverrideConfig loadDirective(File file, String path) throws IOException { // TODO: load superdirectory directives.
		if (!file.exists()) return null;
		OverrideConfig cfg = new OverrideConfig(file);
		cfg.load();
		cConfigCache.put(path, cfg);
		return cfg;
	}
	
	public String getSuperDirectory(String path) {
		return path.contains("/") ? path.substring(0, path.lastIndexOf("/") + 1) : path;
	}
	
	public Resource preloadOverride(RequestPacket request, Resource resource, String htds) throws IOException {
		if (resource == null) return null;
		String rt = request.target;
		if (rt.contains("#")) {
			rt = rt.substring(0, rt.indexOf("#"));
		}
		if (rt.contains("?")) {
			rt = rt.substring(0, rt.indexOf("?"));
		}
		String nrt = getSuperDirectory(request.host.getHostPath() + rt);
		if (cConfigCache.containsKey(nrt)) {
			resource.effectiveOverride = cConfigCache.get(nrt);
		}else {
			File abs = getAbsolutePath(rt, request).getParentFile();
			File override = null;
			do {
				if (!abs.exists()) abs = abs.getParentFile();
				File no = new File(abs, ".override");
				if (no.isFile()) {
					override = no;
					continue;
				}else {
					abs = abs.getParentFile();
					if (!abs.getAbsolutePath().startsWith(htds)) break;
				}
			}while (override == null);
			if (override != null) resource.effectiveOverride = loadDirective(new File(abs, ".override"), nrt);
		}
		return resource;
	}
	
	public Resource getResource(String reqTarget, RequestPacket request) {
		try {
			String rt = reqTarget;
			if (rt.contains("#")) {
				rt = rt.substring(0, rt.indexOf("#"));
			}
			if (rt.contains("?")) {
				rt = rt.substring(0, rt.indexOf("?"));
			}
			String nrt = request.host.getHostPath() + rt; // TODO: overlapping htdocs caching w/o file io
			String superdir = getSuperDirectory(nrt);
			byte[] resource = null;
			String ext = "";
			boolean lwi = false;
			boolean tooBig = false;
			OverrideConfig directive = null;
			String oabs = null;
			if (cache.containsKey(nrt)) {
				long t = System.currentTimeMillis();
				long cc = Integer.parseInt(request.host.getHost().getConfig().getNode("cacheClock").getValue());
				if (request.overrideCache >= -1) {
					cc = request.overrideCache;
				}
				boolean tc = cc > 0 && t - cc < cacheClock;
				if (tc || cc == -1 || extCache.get(nrt).equals("application/x-java")) {
					resource = cache.get(nrt);
					if (resource == null) {
						return null;
					}
					ext = extCache.get(nrt);
					lwi = lwiCache.get(nrt);
					tooBig = tbCache.get(nrt);
					oabs = absCache.get(nrt);
					directive = cConfigCache.get(superdir);
				}else if (!tc && cc > 0) {
					cacheClock = t;
					String[] delKeys = new String[cache.size()];
					int delSize = 0;
					for (String file : cache.keySet()) {
						if (!extCache.get(file).equals("application/x-java")) {
							delKeys[delSize++] = file;
						}
					}
					for (int i = 0; i < delSize; i++) {
						cache.remove(delKeys[i]);
						extCache.remove(delKeys[i]);
						lwiCache.remove(delKeys[i]);
						absCache.remove(delKeys[i]);
						tbCache.remove(delKeys[i]);
					}
					cConfigCache.clear();
					((PatchInline)request.host.getHost().registry.getPatchForClass(PatchInline.class)).clearCache();
				}
			}
			if (resource == null) {
				File abs = getAbsolutePath(rt, request);
				if (!cConfigCache.containsKey(superdir) && abs != null) {
					directive = loadDirective(new File(abs.getParentFile(), ".override"), superdir);
				}
				oabs = abs.getAbsolutePath();
				if (abs != null && abs.exists()) {
					ext = abs.getName().substring(abs.getName().lastIndexOf(".") + 1);
					ext = AvunaHTTPD.extensionToMime.containsKey(ext) ? AvunaHTTPD.extensionToMime.get(ext) : "application/octet-stream";
					FileInputStream fin = new FileInputStream(abs);
					ByteArrayOutputStream bout = new ByteArrayOutputStream();
					int i = 1;
					byte[] buf = new byte[4096];
					while (i > 0) {
						i = fin.read(buf);
						if (i > 0) {
							bout.write(buf, 0, i);
						}
						PatchChunked chunked = (PatchChunked)request.host.getHost().registry.getPatchForClass(PatchChunked.class);
						if (chunked.pcfg.getNode("enabled").getValue().equals("true") && bout.size() > Integer.parseInt(chunked.pcfg.getNode("minsize").getValue()) && !ext.startsWith("application")) {
							bout.reset();
							tooBig = true;
							break;
						}
					}
					fin.close();
					resource = bout.toByteArray();
				}else {
					cache.put(nrt, null);
					extCache.put(nrt, "text/html");
					lwi = this.lwi;
					lwiCache.put(nrt, lwi);
					absCache.put(nrt, oabs);
					tbCache.put(nrt, false);
					return null;
				}
				cache.put(nrt, resource);
				extCache.put(nrt, ext);
				lwi = this.lwi;
				lwiCache.put(nrt, lwi);
				absCache.put(nrt, oabs);
				tbCache.put(nrt, tooBig);
			}
			Resource r = new Resource(resource, ext, rt, directive, oabs);
			r.wasDir = lwi;
			r.tooBig = tooBig;
			return r;
		}catch (IOException e) {
			if (!(e instanceof FileNotFoundException)) Logger.logError(e);
			return null;
		}
	}
}
