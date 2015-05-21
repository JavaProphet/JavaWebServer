package org.avuna.httpd;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Scanner;
import org.avuna.httpd.com.ComClient;
import org.avuna.httpd.com.CommandContext;
import org.avuna.httpd.com.CommandRegistry;
import org.avuna.httpd.com.base.CommandComp;
import org.avuna.httpd.com.base.CommandExit;
import org.avuna.httpd.com.base.CommandHTML;
import org.avuna.httpd.com.base.CommandHTMLDir;
import org.avuna.httpd.com.base.CommandHelp;
import org.avuna.httpd.com.base.CommandMem;
import org.avuna.httpd.com.base.CommandPHP;
import org.avuna.httpd.com.base.CommandRegister;
import org.avuna.httpd.com.base.CommandReload;
import org.avuna.httpd.com.base.CommandRestart;
import org.avuna.httpd.com.base.CommandSelect;
import org.avuna.httpd.dns.RecordHolder;
import org.avuna.httpd.hosts.Host;
import org.avuna.httpd.hosts.HostCom;
import org.avuna.httpd.hosts.HostDNS;
import org.avuna.httpd.hosts.HostHTTP;
import org.avuna.httpd.hosts.HostHTTPM;
import org.avuna.httpd.hosts.HostMail;
import org.avuna.httpd.hosts.HostRegistry;
import org.avuna.httpd.hosts.Protocol;
import org.avuna.httpd.util.CLib;
import org.avuna.httpd.util.Config;
import org.avuna.httpd.util.ConfigFormat;
import org.avuna.httpd.util.ConfigNode;
import org.avuna.httpd.util.FileManager;
import org.avuna.httpd.util.Logger;
import org.avuna.httpd.util.SafeMode;

public class AvunaHTTPD {
	public static final String VERSION = "1.2.1";
	public static Config mainConfig, hostsConfig;
	public static final FileManager fileManager = new FileManager();
	public static final HashMap<String, String> extensionToMime = new HashMap<String, String>();
	public static final String crlf = new String(new byte[]{13, 10});
	public static final byte[] crlfb = new byte[]{13, 10};
	public static final CommandRegistry commandRegistry = new CommandRegistry();
	static {
		commandRegistry.registerCommand(new CommandHelp(), "help");
		commandRegistry.registerCommand(new CommandExit(), "exit", "stop");
		commandRegistry.registerCommand(new CommandComp(), "comp");
		commandRegistry.registerCommand(new CommandHTML(), "html");
		commandRegistry.registerCommand(new CommandHTMLDir(), "htmldir");
		commandRegistry.registerCommand(new CommandMem(), "mem");
		commandRegistry.registerCommand(new CommandPHP(), "php");
		commandRegistry.registerCommand(new CommandRegister(), "register");
		commandRegistry.registerCommand(new CommandReload(), "reload");
		commandRegistry.registerCommand(new CommandRestart(), "restart");
		commandRegistry.registerCommand(new CommandSelect(), "select");
	}
	public static final CommandContext mainCommandContext = commandRegistry.newContext(System.out, new Scanner(System.in));
	
	public static void setupFolders() {
		fileManager.getMainDir().mkdirs();
		fileManager.getLogs().mkdirs();
		for (Host host : hosts.values()) {
			host.setupFolders();
		}
	}
	
	public static void setupScripts() throws IOException {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("windows")) {
			File f = fileManager.getBaseFile("run.bat");
			if (!f.exists()) {
				FileOutputStream fout = new FileOutputStream(f);
				fout.write(("javaw -jar \"" + fileManager.getBaseFile("avuna.jar").getAbsolutePath() + "\" \"" + fileManager.getBaseFile("main.cfg").getAbsolutePath() + "\"").getBytes());
				fout.flush();
				fout.close();
			}
			f = fileManager.getBaseFile("kill.bat");
			if (!f.exists()) {
				FileOutputStream fout = new FileOutputStream(f);
				fout.write(("taskkill /f /im javaw").getBytes());
				fout.flush();
				fout.close();
			}
			f = fileManager.getBaseFile("restart.bat");
			if (!f.exists()) {
				FileOutputStream fout = new FileOutputStream(f);
				fout.write(("kill.bat & run.bat").getBytes());
				fout.flush();
				fout.close();
			}
			f = fileManager.getBaseFile("cmd.bat");
			if (!f.exists()) {
				FileOutputStream fout = new FileOutputStream(f);
				fout.write(("java -jar \"" + fileManager.getBaseFile("avuna.jar").getAbsolutePath() + "\" cmd").getBytes());
				fout.flush();
				fout.close();
			}
		}else {
			String ll = new String(new byte[]{0x0A});
			File f = fileManager.getBaseFile("run.sh");
			if (!f.exists()) {
				FileOutputStream fout = new FileOutputStream(f);
				fout.write(("#!/bin/bash" + ll + ll + "nohup java -jar \"" + fileManager.getBaseFile("avuna.jar").getAbsolutePath() + "\" \"" + fileManager.getBaseFile("main.cfg").getAbsolutePath() + "\" >& /dev/null &").getBytes());
				fout.flush();
				fout.close();
			}
			f = fileManager.getBaseFile("kill.sh");
			if (!f.exists()) {
				FileOutputStream fout = new FileOutputStream(f);
				fout.write(("#!/bin/bash" + ll + ll + "pkill -f " + fileManager.getBaseFile("avuna.jar").getAbsolutePath() + "").getBytes());
				fout.flush();
				fout.close();
			}
			f = fileManager.getBaseFile("restart.sh");
			if (!f.exists()) {
				FileOutputStream fout = new FileOutputStream(f);
				fout.write(("#!/bin/bash" + ll + ll + "" + fileManager.getBaseFile("kill.sh").getAbsolutePath() + ll + fileManager.getBaseFile("run.sh").getAbsolutePath()).getBytes());
				fout.flush();
				fout.close();
			}
			f = fileManager.getBaseFile("cmd.sh");
			if (!f.exists()) {
				FileOutputStream fout = new FileOutputStream(f);
				fout.write(("#!/bin/bash" + ll + "java -jar \"" + fileManager.getBaseFile("avuna.jar").getAbsolutePath() + "\" cmd").getBytes());
				fout.flush();
				fout.close();
			}
		}
	}
	
	public static void unpack() {
		try {
			setupScripts();
			String[] unpacks = new String[]{"mime.txt", "jni/amd64/libAvunaHTTPD_JNI.so", "jni/i386/libAvunaHTTPD_JNI.so"};
			for (String up : unpacks) {
				if (windows && up.endsWith(".so")) continue;
				if (!windows && up.endsWith(".dll")) continue;
				File mime = fileManager.getBaseFile(up);
				mime.getParentFile().mkdirs();
				if (!mime.exists()) {
					Logger.log("Unpacking " + up + "...");
					InputStream in = AvunaHTTPD.class.getResourceAsStream("/unpack/" + up);
					int i = 1;
					byte[] buf = new byte[4096];
					ByteArrayOutputStream bout = new ByteArrayOutputStream();
					while (i > 0) {
						i = in.read(buf);
						if (i > 0) {
							bout.write(buf, 0, i);
						}
					}
					in.close();
					FileOutputStream fout = new FileOutputStream(mime);
					fout.write(bout.toByteArray());
					fout.flush();
					fout.close();
				}
			}
		}catch (IOException e) {
			Logger.logError(e);
		}
	}
	
	public static void loadUnpacked() {
		try {
			File mime = fileManager.getBaseFile("mime.txt");
			Scanner s = new Scanner(new FileInputStream(mime));
			while (s.hasNextLine()) {
				String line = s.nextLine().trim();
				if (line.length() > 0) {
					String[] ls = line.split(" ");
					if (ls.length > 1) {
						for (int i = 1; i < ls.length; i++) {
							extensionToMime.put(ls[i], ls[0]);
						}
					}
				}
			}
			s.close();
		}catch (IOException e) {
			Logger.logError(e);
		}
	}
	
	public static final HashMap<String, Host> hosts = new HashMap<String, Host>();
	
	public static final RecordHolder records = new RecordHolder();
	
	public static final ArrayList<String> bannedIPs = new ArrayList<String>();
	
	public static long lastbipc = 0L;
	public static final boolean windows = System.getProperty("os.name").toLowerCase().contains("windows");
	
	public static void main(String[] args) {
		try {
			boolean dosetid = false;
			if (args.length >= 1) {
				if (args[0].equals("cmd")) {
					String ip = args.length >= 2 ? args[1] : "127.0.0.1";
					int port = args.length >= 3 ? Integer.parseInt(args[2]) : 6049;
					ComClient.run(ip, port);
					return;
				}else if (args[0].equals("ucmd")) {
					if (windows) {
						System.out.println("MUST be on a unix system!");
						return;
					}
					if (args.length != 2) {
						System.out.println("MUST specify unix socket file!");
						return;
					}
					ComClient.runUnix(args[1]);
					return;
				}else if (args[0].equals("setid")) {
					if (windows) {
						System.out.println("Unix only!");
						return;
					}
					if (args.length < 4) {
						System.out.println("Usage: setid <mainConfigFile> <uid> <gid> <exec...>");
						return;
					}
					dosetid = true;
				}
				
			}
			
			System.setProperty("line.separator", crlf);
			final boolean unpack = args.length >= 1 && args[0].equals("unpack");
			File us = null;
			try {
				us = new File(AvunaHTTPD.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
			}catch (Exception e) {
			}
			File fcfg = null;
			if (dosetid) {
				System.out.println(args[1]);
				fcfg = new File(args[1]);
			}else if ((unpack && args.length == 2) || (!unpack && args.length == 1)) {
				fcfg = new File(args[unpack ? 1 : 0]);
			}else if (us != null) {
				fcfg = new File(us.getParentFile(), "main.cfg");
			}else {
				fcfg = new File((windows ? "C:\\avuna\\main.cfg" : "/etc/avuna/main.cfg"));
			}
			final File cfg = fcfg;
			mainConfig = new Config("main", cfg, new ConfigFormat() {
				public void format(ConfigNode map) {
					File dir = null;
					if (!map.containsNode("dir")) {
						map.insertNode("dir", (dir = cfg.getParentFile()).getAbsolutePath());
					}else {
						dir = new File(map.getNode("dir").getValue());
					}
					if (!map.containsNode("hosts")) map.insertNode("hosts", new File(dir, "hosts.cfg").toString());
					if (!map.containsNode("logs")) map.insertNode("logs", new File(dir, "logs").toString());
					if (!map.containsNode("javac")) map.insertNode("javac", "javac");
					if (!windows && !map.containsNode("uid")) map.insertNode("uid", unpack ? "6833" : "0");
					if (!windows && !map.containsNode("gid")) map.insertNode("gid", unpack ? "6833" : "0");
					if (!windows && !map.containsNode("safeMode")) map.insertNode("safeMode", "true");
				}
			});
			mainConfig.load();
			if (unpack) {
				mainConfig.save();
			}
			unpack();
			loadUnpacked();
			// {
			// final UnixServerSocket uss = new UnixServerSocket("/tmp2.sock");
			// uss.bind();
			// new Thread() {
			// public void run() {
			// try {
			// UnixSocket ss = uss.accept();
			// DataOutputStream out = new DataOutputStream(ss.getOutputStream());
			// out.flush();
			// DataInputStream in = new DataInputStream(ss.getInputStream());
			// out.write(10);
			// out.write(10);
			// out.write(10);
			// out.flush();
			// System.out.println(in.available());
			// System.out.println(in.read());
			// System.out.println(in.read());
			// System.out.println(in.read());
			// ss.close();
			// }catch (IOException e) {
			// e.printStackTrace();
			// }
			// }
			// }.start();
			// long start = System.nanoTime();
			// UnixSocket cs = new UnixSocket("/tmp2.sock");
			// DataOutputStream out = new DataOutputStream(cs.getOutputStream());
			// out.flush();
			// DataInputStream in = new DataInputStream(cs.getInputStream());
			// System.out.println(in.read());
			// System.out.println(in.read());
			// System.out.println(in.read());
			// out.write(10);
			// out.write(10);
			// out.write(10);
			// out.flush();
			// cs.close();
			// uss.close();
			// if (true) return;
			// }
			if (dosetid) {
				int wuid = Integer.parseInt(args[2]);
				int wgid = Integer.parseInt(args[3]);
				CLib.setgid(wgid);
				CLib.setuid(wuid);
				int uid = CLib.getuid();
				int gid = CLib.getgid();
				System.out.println("setuid = " + uid + " (wanted " + wuid + ")");
				System.out.println("setgid = " + gid + " (wanted " + wgid + ")");
				if (uid != wuid || gid != wgid) {
					System.out.println("Failed to de-escalate, terminating!");
					return;
				}
				String[] rargs = new String[args.length - 4];
				System.arraycopy(args, 4, rargs, 0, rargs.length);
				ProcessBuilder pb = new ProcessBuilder(rargs);
				pb.redirectErrorStream(true);
				Process p = pb.start();
				p.waitFor();
				InputStream in = p.getInputStream();
				while (in.available() > 0) {
					System.out.append((char)in.read());
				}
				return;
			}
			if (!windows && CLib.getuid() == 0) {
				System.out.println("[NOTIFY] Running as root, will load servers and attempt de-escalate, if configured.");
			}
			HostRegistry.addHost(Protocol.HTTP, HostHTTP.class);
			HostRegistry.addHost(Protocol.HTTPM, HostHTTPM.class);
			HostRegistry.addHost(Protocol.COM, HostCom.class);
			HostRegistry.addHost(Protocol.DNS, HostDNS.class);
			HostRegistry.addHost(Protocol.MAIL, HostMail.class);
			HostHTTP.unpack();
			HostCom.unpack();
			HostDNS.unpack();
			HostMail.unpack();
			hostsConfig = new Config("hosts", new File(mainConfig.getNode("hosts").getValue()), new ConfigFormat() {
				
				@Override
				public void format(ConfigNode map) {
					boolean nm = false, nc = false, nd = false;
					if (!map.containsNode("main")) {
						map.insertNode("main");
						nm = true;
					}
					if (!map.containsNode("com")) {
						map.insertNode("com");
						nc = true;
					}
					// if (!map.containsKey("dns")) {
					// map.put("dns", new LinkedHashMap<String, Object>());
					// nd = true;
					// }
					for (String key : map.getSubnodes()) {
						ConfigNode host = map.getNode(key);
						if (!host.containsNode("enabled")) host.insertNode("enabled", (nc && key.equals("com")) ? "false" : "true");
						if (!host.containsNode("protocol")) host.insertNode("protocol", ((nd && key.equals("dns")) ? "dns" : ((nc && key.equals("com")) ? "com" : "http")));
						Protocol p = Protocol.fromString(host.getNode("protocol").getValue());
						if (p == null) {
							Logger.log("Skipping Host: " + key + " due to invalid protocol!");
							continue;
						}
						if (!host.getNode("enabled").getValue().equals("true")) {
							continue;
						}
						try {
							Host h = (Host)HostRegistry.getHost(p).getConstructors()[0].newInstance(key);
							h.formatConfig(host);
							hosts.put(key, h);
						}catch (Exception e) {
							Logger.logError(e);
							continue;
						}
					}
				}
				
			});
			hostsConfig.load();
			hostsConfig.save();
			setupFolders();
			File lf = new File(fileManager.getLogs(), "" + (System.currentTimeMillis() / 1000L));
			lf.createNewFile();
			Logger.INSTANCE = new Logger(new PrintStream(new FileOutputStream(lf)));
			Logger.log("Loaded Configs");
			for (Host host : hosts.values()) {
				if (host instanceof HostHTTP) {
					((HostHTTP)host).loadBases();
				}
			}
			if (unpack) {
				Logger.log("Unpack complete, terminating.");
				Logger.flush();
				System.exit(0);
			}
			Logger.log("Loading Connection Handling");
			for (Host h : hosts.values()) {
				if (!h.hasStarted()) h.start();
			}
			if (!windows && mainConfig.getNode("safeMode").getValue().equals("true")) {
				SafeMode.setPerms(cfg.getParentFile(), Integer.parseInt(mainConfig.getNode("uid").getValue()), Integer.parseInt(mainConfig.getNode("gid").getValue()));
			}
			if (!windows && CLib.getuid() == 0 && !mainConfig.getNode("uid").getValue().equals("0")) {
				major:
				while (true) {
					for (Host h : hosts.values()) {
						if (!h.loaded) {
							Thread.sleep(1L);
							continue major;
						}
					}
					break;
				}
				CLib.setuid(Integer.parseInt(mainConfig.getNode("uid").getValue()));
				CLib.setgid(Integer.parseInt(mainConfig.getNode("gid").getValue()));
				Logger.log("[NOTIFY] De-escalated to uid " + CLib.getuid());
			}else if (!windows) {
				Logger.log("[NOTIFY] We did NOT de-escalate, currently running as uid " + CLib.getuid());
			}
			for (Host host : hosts.values()) {
				if (host instanceof HostHTTP) {
					((HostHTTP)host).loadCustoms();
				}
			}
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					Logger.log("Softly Terminating!");
					for (Host h : hosts.values()) {
						h.preExit();
					}
					Logger.flush();
				}
			});
		}catch (Exception e) {
			if (Logger.INSTANCE == null) {
				e.printStackTrace();
			}else {
				Logger.logError(e);
			}
		}
		Scanner scan = new Scanner(System.in);
		while (scan.hasNextLine()) {
			try {
				String command = scan.nextLine();
				commandRegistry.processCommand(command, mainCommandContext);
			}catch (NoSuchElementException fe) {
				break;
			}catch (Exception e) {
				Logger.logError(e);
			}
		}
	}
}
