package com.javaprophet.javawebserver;

import java.util.ArrayList;

public class ContentEncoding {
	private static final ArrayList<ContentEncoding> ces = new ArrayList<ContentEncoding>();
	public static final ContentEncoding compress = new ContentEncoding("compress");
	public static final ContentEncoding deflate = new ContentEncoding("deflate");
	public static final ContentEncoding exi = new ContentEncoding("exi");
	public static final ContentEncoding gzip = new ContentEncoding("gzip");
	public static final ContentEncoding identity = new ContentEncoding("identity");
	public static final ContentEncoding pack200gzip = new ContentEncoding("pack200-gzip");
	public static final ContentEncoding xcompress = new ContentEncoding("x-compress");
	public static final ContentEncoding xgzip = new ContentEncoding("x-gzip");
	public String name = "";
	
	private ContentEncoding(String name) {
		this.name = name;
		ces.add(this);
	}
	
	public String toString() {
		if (this == identity) {
			return "";
		}else {
			return name;
		}
	}
	
	public static ContentEncoding get(String name) {
		if (name.equals("")) return identity;
		for (ContentEncoding ce : ces) {
			if (ce.name.equals(name)) {
				return ce;
			}
		}
		return null;
	}
}