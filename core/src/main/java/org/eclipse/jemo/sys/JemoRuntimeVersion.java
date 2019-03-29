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

import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.sys.auth.JemoUser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class JemoRuntimeVersion {
	public static void processRequest(JemoUser user, HttpServletRequest request, HttpServletResponse response) throws IOException,ParserConfigurationException,SAXException {
		switch(request.getMethod()) {
			case "GET":
				response.setContentType("text/plain");
				Jemo.stream(response.getOutputStream(), new ByteArrayInputStream(getVersion().getBytes("UTF-8")));
				break;
			default:
				response.sendError(401);
		}
	}
	
	public static String getVersion() throws IOException,ParserConfigurationException,SAXException {
		InputStream in = Jemo.class.getResourceAsStream("/META-INF/maven/org.eclipse.jemo/jemo/pom.xml");
		if(in != null) {
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Jemo.class.getResourceAsStream("/META-INF/maven/org.eclipse.jemo/jemo/pom.xml"));
			NodeList docChildList = doc.getDocumentElement().getChildNodes();
			for(int i = 0; i < docChildList.getLength(); i++) {
				Node nItem = docChildList.item(i);
				if(nItem.getNodeName().equals("version")) {
					return nItem.getTextContent();
				}
			}
		}
		
		return "1.0";
	}
}
