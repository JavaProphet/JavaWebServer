package org.avuna.httpd.hosts;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import org.avuna.httpd.AvunaHTTPD;
import org.avuna.httpd.dns.RecordHolder;
import org.avuna.httpd.dns.TCPServer;
import org.avuna.httpd.dns.ThreadDNSWorker;
import org.avuna.httpd.dns.UDPServer;
import org.avuna.httpd.util.Logger;

public class HostDNS extends Host {
	
	public HostDNS(String name) {
		super(name, Protocol.DNS);
	}
	
	private String ip = null, dnsf = null;
	private int twc, mc;
	private int port;
	
	public static void unpack() {
		try {
			AvunaHTTPD.fileManager.getBaseFile("dns.cfg").createNewFile();
		}catch (IOException e) {
			Logger.logError(e);
		}
	}
	
	public void formatConfig(HashMap<String, Object> map) {
		if (!map.containsKey("port")) map.put("port", "53");
		if (!map.containsKey("ip")) map.put("ip", "0.0.0.0");
		if (!map.containsKey("dnsf")) map.put("dnsf", AvunaHTTPD.fileManager.getBaseFile("dns.cfg").getAbsolutePath());
		dnsf = (String)map.get("dnsf");
		ip = (String)map.get("ip");
		port = Integer.parseInt((String)map.get("port"));
		if (!map.containsKey("workerThreadCount")) map.put("workerThreadCount", "8");
		if (!map.containsKey("maxConnections")) map.put("maxConnections", "-1");
		twc = Integer.parseInt((String)map.get("workerThreadCount"));
		mc = Integer.parseInt((String)map.get("maxConnections"));
	}
	
	public void setup(ServerSocket s) {
		RecordHolder holder = new RecordHolder(new File(dnsf));
		ThreadDNSWorker.holder = holder;
		ThreadDNSWorker.initQueue(mc < 1 ? 10000000 : mc);
		for (int i = 0; i < twc; i++) {
			ThreadDNSWorker worker = new ThreadDNSWorker();
			worker.setDaemon(true);
			worker.start();
		}
		UDPServer udp = new UDPServer();
		udp.start();
		TCPServer tcp = new TCPServer(s);
		tcp.start();
	}
}
