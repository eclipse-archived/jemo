/*
********************************************************************************
* Copyright (c) 9th November 2018 Cloudreach Limited Europe
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* This Source Code may also be made available under the following Secondary
* Licenses when the conditions for such availability set forth in the Eclipse
* Public License, v. 2.0 are satisfied: GNU General Public License, version 2
* with the GNU Classpath Exception which is
* available at https://www.gnu.org/software/classpath/license.html.
*
* SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
********************************************************************************/
package org.eclipse.jemo.sys;

import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.sys.auth.JemoUser;

import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This plugin manager module for Jemo will return the runtime statistics for the system
 * based on a specific execution interval.
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class JemoRuntimeStatistics {
	public static final String REQ_INTERVAL_KEY = "ITK";
	
	private final AbstractJemo jemoServer;
	
	public JemoRuntimeStatistics(AbstractJemo jemoServer) {
		this.jemoServer = jemoServer;
	}
	
	public void processRequest(JemoUser user, HttpServletRequest request, HttpServletResponse response) throws IOException {
		switch(request.getMethod()) {
			case "GET":
				response.setContentType("application/json");
				try(OutputStream out = response.getOutputStream()) {
					JemoPluginManager.MonitoringInterval interval = jemoServer.getPluginManager().getMonitoringInterval(request.getParameter(REQ_INTERVAL_KEY));
					byte[] outputBytes;
					if(interval == null) {
						outputBytes = Jemo.toJSONString(jemoServer.getPluginManager().listMonitoringIntervals()).getBytes("UTF-8");
					} else {
						outputBytes = Jemo.toJSONString(interval).getBytes("UTF-8");
					}
					response.setContentLength(outputBytes.length);
					out.write(outputBytes);
					out.flush();
				}
				break;
			default:
				response.sendError(401);
		}
	}
}
