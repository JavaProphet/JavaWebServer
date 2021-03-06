/* Avuna HTTPD - General Server Applications Copyright (C) 2015 Maxwell Bruce This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>. */

package org.avuna.httpd.ftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Random;
import org.avuna.httpd.AvunaHTTPD;
import org.avuna.httpd.hosts.HostFTP;
import org.avuna.httpd.mail.util.StringFormatter;
import org.avuna.httpd.util.CException;
import org.avuna.httpd.util.CLib;
import org.avuna.httpd.util.SafeMode;
import org.avuna.httpd.util.SafeMode.StatResult;

public class FTPHandler {
	
	public final ArrayList<FTPCommand> commands = new ArrayList<FTPCommand>();
	private final Random rand = new Random();
	private static final SimpleDateFormat mdtm = new SimpleDateFormat("yyyyMMddHHmmss");
	
	public static boolean checkPerms(int uid, int gid, File f, boolean fileOnly, boolean dirOnly, boolean read, boolean write, boolean execute, boolean mustExist, boolean allowInheritance) throws CException {
		if (CLib.failed) return true;
		if (uid < 0 || gid < 0) return false;
		if (mustExist && !f.exists()) return false;
		if (f.exists()) {
			if (fileOnly && !f.isFile()) return false;
			if (mustExist && dirOnly && !f.isDirectory()) return false;
			if (read && !f.canRead()) return false;
			if (write && !f.canWrite()) return false;
			if (execute && !f.canExecute()) return false;
		}
		if (mustExist || f.exists()) {
			StatResult sr = new SafeMode.StatResult(f.getAbsolutePath());
			if (read && !SafeMode.canUserRead(uid, gid, sr)) return false;
			if (write && !SafeMode.canUserWrite(uid, gid, sr)) return false;
			if (execute && !SafeMode.canUserExecute(uid, gid, sr)) return false;
			return true;
		}
		File pf = f.getParentFile();
		if (allowInheritance && !read && pf != null && pf.exists()) {
			StatResult sr = new SafeMode.StatResult(pf.getAbsolutePath());
			if (write && !SafeMode.canUserRead(uid, gid, sr)) return false;
			if (!SafeMode.canUserExecute(uid, gid, sr)) return false;
			return true;
		}
		return false;
	}
	
	public static File chroot(File root, String cwd, String file) throws CException {
		File rf = null;
		if (file.startsWith("/")) {
			rf = root;
		}else {
			rf = new File(root, cwd);
		}
		rf = new File(rf, file);
		rf = SafeMode.resolveLinks(rf);
		if (!rf.getAbsolutePath().startsWith(root.getAbsolutePath())) return null;
		if (SafeMode.isHardlink(rf)) return null;
		return rf;
	}
	
	public FTPHandler(final HostFTP host) {
		commands.add(new FTPCommand("user", 0, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeLine(331, "Please specify the password.");
				focus.user = line;
			}
		});
		commands.add(new FTPCommand("acct", 0, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeLine(502, "ACCT not implemented.");
			}
		});
		commands.add(new FTPCommand("pass", 0, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (focus.user == null || focus.user.length() == 0) {
					focus.writeLine(503, "Login with USER first.");
					return;
				}
				if (host.provider.isValid(focus.user, line)) {
					focus.writeLine(230, "Login successful.");
					focus.auth = true;
					focus.state = 1;
					focus.root = host.provider.getRoot(focus.user);
				}else {
					focus.writeLine(531, "Login incorrect.");
				}
			}
		});
		commands.add(new FTPCommand("quit", 0, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeLine(221, "Goodbye.");
				focus.close();
			}
		});
		commands.add(new FTPCommand("cwd", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, false, true, true, false, true, true, false)) {
					focus.writeLine(550, "Failed to open directory.");
					return;
				}
				if (f.canRead()) {
					focus.cwd = chroot(focus.root, f);
					focus.writeLine(250, "Directory successfully changed.");
				}else {
					focus.writeLine(550, "Failed to open directory.");
				}
				
			}
		});
		commands.add(new FTPCommand("cdup", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (!focus.cwd.equals("/")) {
					if (focus.cwd.endsWith("/")) focus.cwd = focus.cwd.substring(0, focus.cwd.length() - 1);
					focus.cwd = focus.cwd.substring(0, focus.cwd.lastIndexOf("/") + 1);
				}
				if (focus.cwd.equals("")) focus.cwd = "/";
				focus.writeLine(250, "Directory successfully changed.");
			}
		});
		commands.add(new FTPCommand("smnt", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeLine(502, "SMNT not implemented.");
			}
		});
		commands.add(new FTPCommand("rein", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeLine(502, "REIN not implemented.");
			}
		});
		commands.add(new FTPCommand("feat", 0, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeBMLine(211, "Extensions supported:");
				focus.writeMMLine("MDTM");
				focus.writeMMLine("PASV");
				focus.writeMMLine("SIZE");
				focus.writeMMLine("UTF8");
				focus.writeLine(211, "End");
			}
		});
		commands.add(new FTPCommand("opts", 0, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				String[] args = line.split(" ");
				if (args.length == 0) {
					focus.writeLine(501, "Option not understood.");
				}
				if (args[0].equalsIgnoreCase("utf8")) {
					if (args.length != 2) {
						focus.writeLine(501, "Option not understood.");
					}
					if (args[1].equalsIgnoreCase("on")) {
						focus.writeLine(200, "Always in UTF8 mode.");
					}else {
						focus.writeLine(501, "Option not understood.");
					}
				}else {
					focus.writeLine(501, "Option not understood.");
				}
			}
		});
		commands.add(new FTPCommand("port", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (focus.isPASV || focus.isPORT) {
					focus.psv.cancel();
					focus.isPASV = false;
					focus.isPORT = false;
					focus.psv = null;
				}
				focus.isPORT = true;
				String[] ls = line.split(",");
				if (ls.length != 6) {
					focus.writeLine(500, "Illegal PORT command.");
					return;
				}
				String ip = "";
				int port = 0;
				try {
					for (int i = 0; i < 6; i++) {
						if (i < 4) {
							ip += (i > 0 ? "." : "") + ls[i];
						}else if (i == 5) {
							port = Integer.parseInt(ls[i]) * 256;
						}else if (i == 6) {
							port += Integer.parseInt(ls[i]);
						}
					}
				}catch (NumberFormatException e) {
					focus.writeLine(500, "Illegal PORT command.");
					return;
				}
				if (!ip.equals(focus.s.getInetAddress().getHostAddress())) {
					focus.writeLine(500, "Illegal PORT command.");
					return;
				}
				focus.psv = new ThreadPassive(host, focus, ip, port);
				focus.psv.start();
				focus.writeLine(200, "PORT command successful. Consider using PASV.");
			}
		});
		commands.add(new FTPCommand("pasv", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (focus.isPASV || focus.isPORT) {
					focus.psv.cancel();
					focus.isPASV = false;
					focus.isPORT = false;
					focus.psv = null;
				}
				focus.isPASV = true;
				ServerSocket ps = null;
				do {
					try {
						int port = rand.nextInt(65534 - 1024) + 1024;
						ps = new ServerSocket(port, 1);
						int minor = (port % 256);
						int major = (port - minor) / 256;
						focus.psv = new ThreadPassive(host, focus, ps);
						focus.psv.start();
						focus.writeLine(227, "Entering Passive Mode (" + host.provider.getExternalIP().replace(".", ",") + "," + major + "," + minor + ").");
					}catch (IOException e) {
						host.logger.logError(e);
						ps = null;
					}
				}while (ps == null);
			}
		});
		commands.add(new FTPCommand("type", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (line.equalsIgnoreCase("a")) {
					focus.type = FTPType.ASCII;
					focus.writeLine(200, "Switching to ASCII mode.");
				}else if (line.equalsIgnoreCase("i") || line.toLowerCase().startsWith("l")) {
					focus.type = FTPType.BINARY;
					focus.writeLine(200, "Switching to Binary mode.");
				}else {
					focus.writeLine(500, "Unrecognised TYPE command.");
				}
			}
		});
		commands.add(new FTPCommand("stru", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (line.equalsIgnoreCase("f")) {
					focus.writeLine(200, "Structure set to F.");
				}else {
					focus.writeLine(504, "Bad STRU command.");
				}
			}
		});
		commands.add(new FTPCommand("mode", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (line.equalsIgnoreCase("s")) {
					focus.writeLine(200, "Mode set to S.");
				}else {
					focus.writeLine(504, "Bad MODE command.");
				}
			}
		});
		commands.add(new FTPCommand("stor", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (!focus.isPASV && !focus.isPORT) {
					focus.writeLine(425, "Use PORT or PASV first.");
					return;
				}
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, true, false, false, true, false, false, true)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				boolean s = false;
				try {
					if (f.exists()) f.delete();
					f.createNewFile();
					SafeMode.setPerms(f, uid, uid, 0640);
					s = true;
				}catch (IOException e) {
					// Logger.logError(e);
				}
				if (s && f.exists() && f.canWrite()) {
					focus.psv.setType(FTPTransferType.STOR, f);
				}else {
					focus.writeLine(553, "Could not create file.");
				}
			}
		});
		commands.add(new FTPCommand("stou", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (!focus.isPASV && !focus.isPORT) {
					focus.writeLine(425, "Use PORT or PASV first.");
					return;
				}
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, true, false, false, true, false, false, true)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				boolean s = false;
				try {
					int i = 1;
					while (f.exists()) {
						f = new File(f.getParentFile(), f.getName() + "." + (i++));
					}
					f.createNewFile();
					SafeMode.setPerms(f, uid, uid, 0640);
					s = true;
				}catch (IOException e) {
					// Logger.logError(e);
				}
				if (s && f.exists() && f.canWrite()) {
					focus.psv.setType(FTPTransferType.STOU, f);
				}else {
					focus.writeLine(553, "Could not create file.");
				}
			}
		});
		commands.add(new FTPCommand("appe", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (!focus.isPASV && !focus.isPORT) {
					focus.writeLine(425, "Use PORT or PASV first.");
					return;
				}
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, true, false, false, true, false, false, true)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				try {
					if (!f.exists()) {
						f.createNewFile();
						SafeMode.setPerms(f, uid, uid, 0640);
					}
				}catch (IOException e) {
					// Logger.logError(e);
				}
				if (f.exists() && f.canWrite()) {
					focus.psv.setType(FTPTransferType.APPE, f);
				}else {
					focus.writeLine(553, "Could not create file.");
				}
			}
		});
		commands.add(new FTPCommand("list", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (!focus.isPASV && !focus.isPORT) {
					focus.writeLine(425, "Use PORT or PASV first.");
					return;
				}
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, false, false, true, false, false, true, false)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				if (f.isFile()) f = f.getParentFile();
				focus.psv.setType(FTPTransferType.LIST, f);
			}
		});
		commands.add(new FTPCommand("nlst", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (!focus.isPASV && !focus.isPORT) {
					focus.writeLine(425, "Use PORT or PASV first.");
					return;
				}
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, false, false, true, false, false, true, false)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				if (f.isFile()) f = f.getParentFile();
				focus.psv.setType(FTPTransferType.NLST, f);
			}
		});
		commands.add(new FTPCommand("retr", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (!focus.isPASV && !focus.isPORT) {
					focus.writeLine(425, "Use PORT or PASV first.");
					return;
				}
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, true, false, true, false, false, true, false)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				focus.psv.setType(FTPTransferType.RETR, f);
			}
		});
		commands.add(new FTPCommand("size", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, true, false, true, false, false, true, false)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				focus.writeLine(213, "" + f.length());
			}
		});
		commands.add(new FTPCommand("dele", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, true, false, false, true, false, true, false)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				f.delete();
				focus.writeLine(250, "Delete operation successful.");
			}
		});
		commands.add(new FTPCommand("rnfr", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, false, false, true, true, false, true, false)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				focus.rnfr = f;
				focus.writeLine(350, "Ready for RNTO.");
			}
		});
		commands.add(new FTPCommand("rnto", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				if (focus.rnfr == null) {
					focus.writeLine(503, "RNFR required first.");
					return;
				}
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, focus.rnfr.isFile(), focus.rnfr.isDirectory(), false, true, false, false, true)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				if (!f.exists()) {
					if (focus.rnfr.isDirectory()) {
						f.mkdirs();
					}else {
						f.createNewFile();
					}
					SafeMode.setPerms(f, uid, uid, 0640);
				}
				try {
					FileInputStream in = new FileInputStream(focus.rnfr);
					FileOutputStream out = new FileOutputStream(f);
					byte[] buf = new byte[1024];
					int i = 1;
					while (i > 0) {
						i = in.read(buf);
						if (i > 0) {
							out.write(buf, 0, i);
						}
					}
					in.close();
					out.flush();
					out.close();
					focus.rnfr.delete();
					focus.rnfr = null;
					focus.writeLine(250, "Rename successful.");
				}catch (IOException e) {
					focus.writeLine(550, "Rename failed.");
					
				}
			}
		});
		commands.add(new FTPCommand("noop", 0, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeLine(200, "noop");
			}
		});
		commands.add(new FTPCommand("allo", 0, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeLine(202, "ALLO command ignored.");
			}
		});
		commands.add(new FTPCommand("abor", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeLine(225, "No transfer to ABOR.");// TODO
			}
		});
		commands.add(new FTPCommand("rest", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				int i = line.length() > 0 ? Integer.parseInt(line) : 0;
				focus.skip = i;
				focus.writeLine(350, "Restart position accepted (" + i + ").");// TODO
			}
		});
		commands.add(new FTPCommand("mdtm", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, false, false, true, false, false, true, false)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				focus.writeLine(213, mdtm.format(f.lastModified()));
			}
		});
		commands.add(new FTPCommand("mkd", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, false, true, false, true, true, false, true)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				if (f.isFile()) f = f.getParentFile();
				if (f.getParentFile() != null && f.getParentFile().exists() && f.getParentFile().canWrite()) {
					f.mkdirs();
					SafeMode.setPerms(f, uid, uid, 0770);
					focus.writeLine(250, "Create directory operation successful.");
				}else {
					focus.writeLine(550, "Create directory operation failed.");
				}
			}
		});
		commands.add(new FTPCommand("rmd", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				File f = chroot(focus.root, focus.cwd, line);
				int uid = host.provider.getUID(focus.user);
				int gid = host.provider.getGID(focus.user);
				if (!checkPerms(uid, gid, f, false, true, false, true, true, true, false)) {
					focus.writeLine(550, "Failed to open file.");
					return;
				}
				if (f.isFile()) f = f.getParentFile();
				if (f.getParentFile() != null && f.getParentFile().exists() && f.getParentFile().canWrite()) {
					f.delete();
					if (!f.exists()) focus.writeLine(250, "Remove directory operation successful.");
					else focus.writeLine(550, "Remove directory operation failed.");
				}else {
					focus.writeLine(550, "Remove directory operation failed.");
				}
			}
		});
		commands.add(new FTPCommand("help", 0, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeBMLine(214, "The following commands are recognized.");
				focus.writeMMLine("ABOR ACCT ALLO APPE CDUP CWD  DELE HELP LIST MDTM MKD  MODE NLST NOOP");
				focus.writeMMLine("PASS PASV PORT PWD  QUIT REIN REST RETR RMD  RNFR RNTO SITE SMNT STAT");
				focus.writeMMLine("STOR STOU STRU SYST TYPE USER FEAT SIZE OPTS");
				focus.writeLine(214, "Help OK.");
			}
		});
		commands.add(new FTPCommand("site", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				String[] args = line.split(" ");
				args = StringFormatter.congealBySurroundings(args, "\"", "\"");
				if (args.length == 0) {
					focus.writeLine(500, "Unknown SITE command.");
					return;
				}
				if (args[0].equalsIgnoreCase("CHMOD")) {
					if (CLib.failed) {
						focus.writeLine(500, "Unable to CHMOD file.");
						return;
					}
					if (args.length != 3) {
						focus.writeLine(500, "Unknown SITE command.");
						return;
					}
					int chmod = -1;
					try {
						chmod = Integer.parseInt(args[1], 8);
					}catch (NumberFormatException e) {
						focus.writeLine(500, "Bad chmod.");
						return;
					}
					String file = args[2];
					File f = chroot(focus.root, focus.cwd, file);
					int uid = host.provider.getUID(focus.user);
					int gid = host.provider.getGID(focus.user);
					if (!checkPerms(uid, gid, f, false, false, false, true, false, true, false)) {
						focus.writeLine(550, "Failed to open file.");
						return;
					}
					if (CLib.chmod(f.getAbsolutePath(), chmod) == 0) {
						focus.writeLine(200, "Chmod successful.");
					}else {
						focus.writeLine(550, "System error when attempted to chmod.");
					}
				}
				focus.writeLine(500, "Unknown SITE command.");
			}
		});
		commands.add(new FTPCommand("syst", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeLine(215, "UNIX Type: L8"); // even on windows, we use a unix-like system for chrooting.
			}
		});
		commands.add(new FTPCommand("pwd", 1, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeLine(257, "\"" + focus.cwd + "\"");
			}
		});
		commands.add(new FTPCommand("stat", 0, 100) {
			public void run(FTPWork focus, String line) throws IOException {
				focus.writeBMLine(211, "FTP server status:");
				focus.writeMMLine("Connected to " + focus.s.getInetAddress().getHostAddress());
				focus.writeMMLine(focus.auth ? ("Logged in as " + focus.user) : "Not logged in");
				focus.writeMMLine("TYPE: " + (focus.type == FTPType.ASCII ? "ASCII" : "BINARY"));
				focus.writeMMLine("No session bandwidth limit");
				focus.writeMMLine("Session timeout in seconds is 10");
				focus.writeMMLine("Control connection is plain text");
				focus.writeMMLine("Data connections will be plain text");
				focus.writeMMLine("At session startup, client count was 1");
				focus.writeMMLine("Avuna HTTPD " + AvunaHTTPD.VERSION);
				focus.writeLine(211, "End of status");
			}
		});
	}
	
	public static String chroot(File root, File f) {
		String rabs = root.getAbsolutePath();
		String abs = f.getAbsolutePath();
		if (!abs.startsWith(rabs)) {
			return null;
		}
		String nabs = abs.substring(rabs.length());
		if (AvunaHTTPD.windows) {
			nabs = nabs.replace("\\", "/");
		}
		if (!nabs.startsWith("/")) nabs = "/" + nabs;
		return nabs;
	}
}
