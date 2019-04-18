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
import org.eclipse.jemo.internal.model.JemoModule;
import org.eclipse.jemo.sys.auth.JemoUser;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class JemoModuleDocumentation {
	
	private Map<String, JemoClassLoader> docModuleCache = new HashMap<>();
	private final AbstractJemo jemoServer;
	
	public JemoModuleDocumentation(AbstractJemo jemoServer) {
		this.jemoServer = jemoServer;
	}
	
	public synchronized void unloadModule(String moduleJar) throws IOException {
		if(docModuleCache.containsKey(moduleJar)) {
			docModuleCache.get(moduleJar).close();
			docModuleCache.remove(moduleJar);
		}
	}
	
	public synchronized JemoClassLoader getDocumentationClassLoader(String moduleJar) throws IOException {
		JemoClassLoader docClassLoader = null;
		Set<JemoModule> moduleList = jemoServer.getPluginManager().loadPluginModules(JemoPluginManager.PLUGIN_ID(moduleJar));
		if(moduleList != null && !moduleList.isEmpty()) {
			docClassLoader = JemoClassLoader.class.cast(moduleList.iterator().next().getModule().getClass().getClassLoader());
		} else if(docModuleCache.containsKey(moduleJar)) {
			return docModuleCache.get(moduleJar);
		} else {
			docClassLoader = jemoServer.getPluginManager().buildPluginClassLoader(moduleJar);
			if(docClassLoader != null) {
				docModuleCache.put(moduleJar, docClassLoader);
			}
		}
		
		return docClassLoader;
	}
	
	public void processRequest(JemoUser user, HttpServletRequest request, HttpServletResponse response) throws IOException {
		if(user == null) {
			//we need to challenge the user to provide authentication details.
			response.setHeader("WWW-Authenticate", "Basic realm=\"User Visible Realm\"");
			response.sendError(401);
		} else {
			//the user has authenticated so they are allowed to access module documentation. we will be expecting the name of the module to get documentation for to be at the end of the url.
			int endModeName = request.getRequestURI().indexOf('/',"/jemo/docs".length()+1);
			if(!(endModeName > "/jemo/docs".length())) {
				response.sendRedirect(request.getRequestURI()+"/");
				return;
			}
			String modJar = endModeName > "/jemo/docs".length() ? request.getRequestURI().substring("/jemo/docs".length()+1,endModeName) : request.getRequestURI().substring("/jemo/docs".length()+1);
			//we should return the apidocs/index.html file if the blob is not null and we end the url with the module name
			String docPage = "apidocs/index.html";
			if(!request.getRequestURI().endsWith(modJar+"/")) {
				docPage = "apidocs"+request.getRequestURI().substring(endModeName);
			}
			if(docPage.toLowerCase().endsWith(".html")) {
				response.setContentType("text/html");
			} else if(docPage.toLowerCase().endsWith(".js")) {
				response.setContentType("application/javascript");
			} else if(docPage.toLowerCase().endsWith(".css")) {
				response.setContentType("text/css");
			} else {
				response.setContentType("text/plain");
			}
			//obviously we don't want to burden the memory with storing stuff just for the sake of documentation, so what we will do is build an JemoClassLoader to extract
			//the documentation and store a reference to it against the documentation module. Whenever a new module is deployed we will tell the documentation
			//module to clear the cache for that jar.
			JemoClassLoader docLoader = getDocumentationClassLoader(modJar);
			if(docLoader != null) {
				InputStream strIn = docLoader.getResourceAsStream(docPage);
				if(strIn != null) {
					Jemo.stream(response.getOutputStream(), strIn, true);
				} else {
					response.sendError(404,String.format("[%s] The documentation file %s does not exist for this module",modJar,docPage));
				}
			} else {
				response.sendError(404,String.format("[%s] The module %s does not exist",modJar));
			}
		}
	}
}
