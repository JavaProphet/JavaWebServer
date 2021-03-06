/*	Avuna HTTPD - General Server Applications
    Copyright (C) 2015 Maxwell Bruce

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.*/

package org.avuna.httpd.mail.smtp;

import java.io.IOException;

public abstract class SMTPCommand {
	public final String comm;
	public final int minState, maxState;
	
	public SMTPCommand(String comm, int minState, int maxState) {
		this.comm = comm;
		this.minState = minState;
		this.maxState = maxState;
	}
	
	public abstract void run(SMTPWork focus, String line) throws IOException;
}
