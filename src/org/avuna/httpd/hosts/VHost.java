package org.avuna.httpd.hosts;

import java.io.File;
import java.net.URL;
import org.avuna.httpd.plugins.javaloader.JavaLoaderSession;

public class VHost {
	private final HostHTTP host;
	private final File htdocs, htsrc;
	private final String name, vhost;
	private JavaLoaderSession jls;
	
	public VHost(String name, HostHTTP host, File htdocs, File htsrc, String vhost) {
		this.name = name;
		this.host = host;
		this.htdocs = htdocs;
		this.htsrc = htsrc;
		this.vhost = vhost;
	}
	
	public void initJLS(URL[] url) {
		this.jls = new JavaLoaderSession(this, url);
	}
	
	public String getName() {
		return name;
	}
	
	private boolean debug = false;
	
	public void setDebug(boolean set) {
		debug = set;
	}
	
	public boolean getDebug() {
		return debug;
	}
	
	public JavaLoaderSession getJLS() {
		return jls;
	}
	
	public void setJLS(JavaLoaderSession jls) {
		this.jls = jls;
	}
	
	public String getVHost() {
		return vhost;
	}
	
	public HostHTTP getHost() {
		return host;
	}
	
	public void setupFolders() {
		htdocs.mkdirs();
		htsrc.mkdirs();
	}
	
	public File getHTDocs() {
		return htdocs;
	}
	
	public File getHTSrc() {
		return htsrc;
	}
	
	public String getHostPath() {
		return name;
	}
}