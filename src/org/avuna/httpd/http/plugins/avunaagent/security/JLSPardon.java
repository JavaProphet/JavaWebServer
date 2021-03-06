/* Avuna HTTPD - General Server Applications Copyright (C) 2015 Maxwell Bruce This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>. */

package org.avuna.httpd.http.plugins.avunaagent.security;

import org.avuna.httpd.http.networking.RequestPacket;
import org.avuna.httpd.http.plugins.avunaagent.AvunaAgentSecurity;
import org.avuna.httpd.util.ConfigNode;

public class JLSPardon extends AvunaAgentSecurity {
	
	private int returnWeight = 0;
	private boolean enabled = true;
	private String[] pardoned = new String[0];
	
	public void formatConfig(ConfigNode map) {
		if (!map.containsNode("returnWeight")) map.insertNode("returnWeight", "-1000");
		if (!map.containsNode("pardoned")) map.insertNode("pardoned", "127.0.0.1,127.0.0.2");
		if (!map.containsNode("enabled")) map.insertNode("enabled", "true");
		this.returnWeight = Integer.parseInt(map.getNode("returnWeight").getValue());
		this.enabled = map.getNode("enabled").getValue().equals("true");
		pardoned = map.getNode("pardoned").getValue().split(",");
	}
	
	public void reload() {
		init();
	}
	
	@Override
	public int check(String ip) {
		if (!enabled) return 0;
		for (String p : pardoned) {
			if (p.trim().equals(ip)) {
				return returnWeight;
			}
		}
		return 0;
	}
	
	@Override
	public int check(RequestPacket req) {
		return 0;
	}
}
