package org.avuna.httpd.mail.imap.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Scanner;
import org.avuna.httpd.AvunaHTTPD;
import org.avuna.httpd.hosts.HostMail;
import org.avuna.httpd.http.plugins.javaloader.lib.Multipart.MultiPartData;
import org.avuna.httpd.mail.imap.IMAPCommand;
import org.avuna.httpd.mail.imap.IMAPWork;
import org.avuna.httpd.mail.mailbox.Email;
import org.avuna.httpd.mail.util.StringFormatter;
import org.avuna.httpd.util.Logger;

public class IMAPCommandFetch extends IMAPCommand {
	
	public IMAPCommandFetch(String comm, int minState, int maxState, HostMail host) {
		super(comm, minState, maxState, host);
	}
	
	protected static void trim(StringBuilder sb) {
		String s = sb.toString().trim();
		sb.setLength(0);
		sb.append(s);
	}
	
	@Override
	public void run(IMAPWork focus, String letters, String[] args) throws IOException {
		if (args.length >= 2) {
			ArrayList<Email> toFetch = focus.selectedMailbox.getByIdentifier(args[0]);
			String tp = args[1];
			if (tp.startsWith("(")) tp = tp.substring(1, tp.length() - 1);
			String[] tps = tp.split(" ");
			tps = StringFormatter.congealBySurroundings(tps, "[", "]");
			for (String tp2 : tps) {
				Logger.log(tp2);
			}
			for (Email e : toFetch) {
				if (tps[0].equals("all")) {
					tps = new String[]{"FLAGS", "INTERNALDATE", "RFC822.SIZE", "ENVELOPE"};
				}else if (tps[0].equals("fast")) {
					tps = new String[]{"FLAGS", "INTERNALDATE", "RFC822.SIZE"};
				}else if (tps[0].equals("full")) {
					tps = new String[]{"FLAGS", "INTERNALDATE", "RFC822.SIZE", "ENVELOPE", "BODY"};
				}
				StringBuilder ret = new StringBuilder().append(e.uid).append(" FETCH (");
				for (String s3 : tps) {
					String s = s3.toLowerCase();
					if (s.equals("bodystructure") || s.equals("body")) {
						ret.append("BODYSTRUCTURE (");
						if (e.mp != null) {
							for (MultiPartData mpd : e.mp.mpds) {
								String ct = mpd.contentType;
								if (ct == null) continue;
								boolean ecs = ct.contains(";");
								String ct1 = ecs ? ct.substring(0, ct.indexOf(";")).trim() : ct;
								ret.append("(");
								ret.append("\"").append(ct1.toUpperCase().replace("/", "\" \"")).append("\"");
								while (ecs) {
									ret.append(" (");
									ct = ct.substring(ct.indexOf(";") + 1);
									ecs = ct.contains(";");
									ct1 = ecs ? ct.substring(0, ct.indexOf(";")).toUpperCase().trim() : ct.toUpperCase().trim();
									ret.append("\"").append(ct1.replace("=", "\" \"")).append("\"");
									ret.append(")");
								}
								ret.append(" NIL NIL");
								String cte = mpd.contentTransferEncoding;
								if (cte == null) cte = "7BIT";
								ret.append(" \"").append(cte.toUpperCase()).append("\"");
								ret.append(" ").append(mpd.data.length);
								int lines = 0;
								if (mpd.data.length > 1) for (int i = 1; i < mpd.data.length; i++) {
									if (mpd.data[i - 1] == AvunaHTTPD.crlfb[0] && mpd.data[i] == AvunaHTTPD.crlfb[1]) {// assumes crlf, however, is always crlf
										lines++;
									}
								}
								ret.append(" ").append(lines);
								ret.append(" NIL NIL NIL");
								ret.append(")");
							}
							ret.append(" \"ALTERNATIVE\" (\"BOUNDARY\" \"");
							ret.append(e.mp.boundary);
							ret.append("\") NIL NIL");
						}else {
							String ct = e.headers.getHeader("Content-Type");
							if (ct == null) continue;
							boolean ecs = ct.contains(";");
							String ct1 = ecs ? ct.substring(0, ct.indexOf(";")).trim() : ct;
							ret.append("\"").append(ct1.toUpperCase().replace("/", "\" \"")).append("\"");
							while (ecs) {
								ret.append(" (");
								ct = ct.substring(ct.indexOf(";") + 1);
								ecs = ct.contains(";");
								ct1 = ecs ? ct.substring(0, ct.indexOf(";")).trim() : ct.toUpperCase().trim();
								ret.append("\"").append(ct1.replace("=", "\" \"")).append("\"");
								ret.append(")");
							}
							ret.append(" NIL NIL");
							String cte = e.headers.getHeader("Content-Transfer-Encoding");
							if (cte == null) cte = "7BIT";
							ret.append(" \"").append(cte.toUpperCase()).append("\"");
							byte[] bbody = e.body.getBytes();
							ret.append(" ").append(e.body.length());
							int lines = 0;
							if (bbody.length > 1) for (int i = 1; i < bbody.length; i++) {
								if (bbody[i - 1] == AvunaHTTPD.crlfb[0] && bbody[i] == AvunaHTTPD.crlfb[1]) {// assumes crlf, however, is always crlf
									lines++;
								}
							}
							ret.append(" ").append(lines);
							ret.append(" NIL NIL NIL");
						}
						ret.append(")");
						trim(ret);
					}else if (s.startsWith("body") || s.equals("rfc822") || s.equals("rfc822.header") || s.equals("rfc822.text")) {
						StringBuilder mhd = new StringBuilder();
						boolean peek = s.startsWith("body.peek") || s.startsWith("rfc822.header");
						if (!peek) {
							if (e.flags.contains("\\Unseen")) e.flags.remove("\\Unseen");
							if (e.flags.contains("\\Seen")) e.flags.add("\\Seen");
						}
						String s2 = s.startsWith("body") ? s.substring(s.indexOf("[") + 1, s.indexOf("]")) : "";
						if (s.equals("rfc822")) {
							s2 = "";
						}else if (s.equals("rfc822.header")) {
							s2 = "header";
						}else if (s.equals("rfc822.text")) {
							s2 = "text";
						}
						if (s2.equals("")) {
							mhd.append(e.data);
						}else {
							String[] kinds = StringFormatter.congealBySurroundings(s2.split(" "), "(", ")");
							for (int i = 0; i < kinds.length; i++) {
								String value = kinds[i];
								if (i != kinds.length - 1 && kinds[i + 1].startsWith("(")) {
									i++;
									value += " " + kinds[i];
								}
								value = value.toLowerCase().trim();
								if (value.equals("header")) {
									LinkedHashMap<String, ArrayList<String>> hdrs = e.headers.getHeaders();
									for (String ss : hdrs.keySet()) {
										ArrayList<String> values = hdrs.get(ss);
										for (String sss : values) {
											mhd.append(ss).append(": ").append(sss).append(AvunaHTTPD.crlf);
										}
									}
								}else if (value.equals("text")) {
									mhd.append(e.body);
								}else if (value.equals("mime")) {
									if (e.headers.hasHeader("content-type")) {
										mhd.append(e.headers.getHeader("content-type"));
									}else {
										mhd.append("text/plain; charset=UTF-8");
									}
								}else if (value.startsWith("header.fields")) {
									boolean limit = value.contains("(");
									String[] limitList = new String[0];
									if (limit) {
										limitList = value.substring(value.indexOf("(") + 1, value.indexOf(")")).split(" ");
									}
									for (String l : limitList) {
										if (e.headers.hasHeader(l)) {
											for (String v : e.headers.getHeaders(l))
												mhd.append(l).append(": ").append(v).append(AvunaHTTPD.crlf);
										}
									}
								}else if (value.startsWith("header.fields.not")) {
									boolean limit = value.contains("(");
									String[] limitList = new String[0];
									if (limit) {
										limitList = value.substring(value.indexOf("(") + 1, value.indexOf(")")).split(" ");
									}
									LinkedHashMap<String, ArrayList<String>> hdrs = e.headers.getHeaders();
									b:
									for (String ss : hdrs.keySet()) {
										for (String l : limitList) {
											if (ss.equalsIgnoreCase(l)) {
												continue b;
											}
										}
										ArrayList<String> values = hdrs.get(ss);
										for (String sss : values) {
											mhd.append(ss).append(": ").append(sss).append(AvunaHTTPD.crlf);
										}
									}
								}
							}
						}
						int sub = 0;
						int max = -1;
						String s5 = s.substring(s.indexOf("]") + 1);
						if (s5.startsWith("<")) {
							if (s5.contains(".")) {
								String ss = s5.substring(1, s5.indexOf("."));
								sub = ss.length() > 0 ? Integer.parseInt(ss) : 0;
								String sm = s5.substring(s5.indexOf(".") + 1, s5.length() - 1);
								max = sm.length() > 0 ? Integer.parseInt(sm) : 0;
							}else sub = Integer.parseInt(s5.substring(1, s5.length() - 1));
						}
						String s4 = s3;
						if (peek && s4.toLowerCase().startsWith("body.peek")) {
							s4 = s4.substring(0, 4) + s4.substring(9);
						}
						String r = mhd.toString();
						if (sub > 0) {
							if (sub >= r.length()) r = "";
							else r = r.substring(sub);
						}
						if (max > 0) {
							if (r.length() >= max) r = r.substring(0, max);
						}
						ret.append(s4).append(" {").append(r.length() - 2).append("}").append(AvunaHTTPD.crlf);
						ret.append(r);
						ret.append(AvunaHTTPD.crlf);
					}else if (s.equals("envelope")) {
						
					}else if (s.equals("flags")) {
						ret.append("FLAGS (");
						for (String flag : e.flags) {
							ret.append(flag).append(" ");
						}
						trim(ret);
						ret.append(")");
					}else if (s.equals("internaldate")) {
						ret.append("INTERNALDATE ");
						Scanner ed = new Scanner(e.data);
						while (ed.hasNextLine()) {
							String line = ed.nextLine().trim();
							if (line.length() > 0) {
								if (!line.contains(":")) continue;
								String hn = line.substring(0, line.indexOf(":")).trim();
								String hd = line.substring(line.indexOf(":") + 1).trim();
								if (hn.equalsIgnoreCase("date")) {
									ret.append("\"").append(hd).append("\"");
								}
							}else {
								break;
							}
						}
						ed.close();
					}else if (s.equals("rfc822.size")) {
						ret.append("RFC822.SIZE " + e.data.length());
					}else if (s.equals("uid")) {
						ret.append("UID " + e.uid);
					}
					ret.append(" ");
				}
				trim(ret);
				ret.append(")");
				focus.writeLine(focus, "*", ret.toString());
			}
			focus.writeLine(focus, letters, "OK");
		}else {
			focus.writeLine(focus, letters, "BAD Missing Arguments.");
		}
	}
}
