/* Avuna HTTPD - General Server Applications Copyright (C) 2015 Maxwell Bruce This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>. */

package org.avuna.httpd.http.plugins.avunaagent.security;

import java.util.LinkedHashMap;
import org.avuna.httpd.http.networking.RequestPacket;
import org.avuna.httpd.http.plugins.avunaagent.AvunaAgentSecurity;
import org.avuna.httpd.util.ConfigNode;
import org.avuna.httpd.util.StringUtil;

public class JLSUserAgent extends AvunaAgentSecurity {
	
	private int returnWeight = 0;
	private boolean enabled = true;
	private String[] ua = null;
	
	public void formatConfig(ConfigNode map) {
		if (!map.containsNode("returnWeight")) map.insertNode("returnWeight", "100");
		if (!map.containsNode("enabled")) map.insertNode("enabled", "true");
		if (!map.containsNode("userAgents")) map.insertNode("userAgents", "wordpress,sql,php,scan");
		this.returnWeight = Integer.parseInt(map.getNode("returnWeight").getValue());
		this.enabled = map.getNode("enabled").getValue().equals("true");
		this.ua = map.getNode("userAgents").getValue().split(",");
	}
	
	public void reload(LinkedHashMap<String, Object> cfg) {
		init();
	}
	
	@Override
	public int check(RequestPacket req) {
		if (!enabled) return 0;
		if (!req.headers.hasHeader("User-Agent")) {
			return returnWeight;
		}
		String ua = StringUtil.toLowerCase(req.headers.getHeader("User-Agent").trim());
		if (StringUtil.containsAny(ua, this.ua)) {
			return returnWeight;
		}
		return 0;
	}
	
	@Override
	public int check(String arg0) {
		return 0;
	}
	
}
