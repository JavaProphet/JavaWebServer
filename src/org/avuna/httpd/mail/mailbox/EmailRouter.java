package org.avuna.httpd.mail.mailbox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Comparator;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import org.avuna.httpd.AvunaHTTPD;
import org.avuna.httpd.hosts.HostMail;
import org.avuna.httpd.util.Logger;
import org.avuna.httpd.util.Stream;

public class EmailRouter {
	public static void route(HostMail host, Email email) {
		for (String to : email.to) {
			if (to.contains("<") && to.contains(">")) {
				to = to.substring(to.indexOf("<") + 1, to.indexOf(">"));
			}
			String dom = to.substring(to.indexOf("@") + 1);
			String[] doms = host.getConfig().getNode("domain").getValue().split(",");
			boolean local = false;
			for (String dommie : doms) {
				if (dommie.trim().equals(dom)) {
					local = true;
					break;
				}
			}
			if (local) {
				for (EmailAccount acct : host.accounts) {
					if (acct.email.equals(to)) {
						acct.deliver(email);
					}
				}
			}else {
				String[] mx = new String[0];
				try {
					mx = lookupMailHosts(dom);
				}catch (NamingException e) {
					e.printStackTrace();
				}
				for (String mxr : mx) {
					Logger.log("Trying " + mxr);
					try {
						Socket rs = new Socket(InetAddress.getByName(mxr), 25);
						DataOutputStream out = new DataOutputStream(rs.getOutputStream());
						out.flush();
						DataInputStream in = new DataInputStream(rs.getInputStream());
						Logger.log(Stream.readLine(in));
						out.write(("EHLO " + doms[0] + AvunaHTTPD.crlf).getBytes());
						Logger.log("EHLO " + doms[0]);
						out.flush();
						String line;
						while (!(line = Stream.readLine(in)).startsWith("250 "))
							Logger.log(line);
						out.write(("MAIL FROM: " + email.from + AvunaHTTPD.crlf).getBytes());
						Logger.log("MAIL FROM: " + email.from);
						out.flush();
						Logger.log(Stream.readLine(in));
						if (!to.startsWith("<")) to = "<" + to + ">";
						out.write(("RCPT TO: " + to + AvunaHTTPD.crlf).getBytes());
						Logger.log("RCPT TO: " + to);
						out.flush();
						Logger.log(Stream.readLine(in));
						out.write(("DATA" + AvunaHTTPD.crlf).getBytes());
						Logger.log("DATA");
						out.flush();
						Logger.log(Stream.readLine(in));
						out.write(email.data.getBytes());
						Logger.log(email.data);
						out.write((AvunaHTTPD.crlf + "." + AvunaHTTPD.crlf).getBytes());
						out.flush();
						Logger.log(Stream.readLine(in));
						rs.close();
						break;
					}catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	// CREDIT: http://www.eyeasme.com/Shayne/MAILHOSTS/mailHostsLookup.html Copyright � 2011 Shayne Steele (shayne.steele@eyeasme.com)
	private static String[] lookupMailHosts(String domainName) throws NamingException {
		InitialDirContext iDirC = new InitialDirContext();
		Attributes attributes = iDirC.getAttributes("dns:/" + domainName, new String[]{"MX"});
		Attribute attributeMX = attributes.get("MX");
		if (attributeMX == null) {
			return (new String[]{domainName});
		}
		String[][] pvhn = new String[attributeMX.size()][2];
		for (int i = 0; i < attributeMX.size(); i++) {
			pvhn[i] = ("" + attributeMX.get(i)).split("\\s+");
		}
		Arrays.sort(pvhn, new Comparator<String[]>() {
			public int compare(String[] o1, String[] o2) {
				return (Integer.parseInt(o1[0]) - Integer.parseInt(o2[0]));
			}
		});
		String[] sortedHostNames = new String[pvhn.length];
		for (int i = 0; i < pvhn.length; i++) {
			sortedHostNames[i] = pvhn[i][1].endsWith(".") ? pvhn[i][1].substring(0, pvhn[i][1].length() - 1) : pvhn[i][1];
		}
		return sortedHostNames;
	}
}
