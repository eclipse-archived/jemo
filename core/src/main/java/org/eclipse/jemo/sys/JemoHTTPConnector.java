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
import org.eclipse.jemo.internal.model.JemoError;
import org.eclipse.jemo.internal.model.JemoModule;

import java.io.IOException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

/**
 * @author christopher stura
 */
public class JemoHTTPConnector {

	private static final Map<String, AbstractJemo> JEMO_SERVER_REFERENCE_MAP = new HashMap<>();

	private AbstractJemo jemoServer;

	public static enum MODE {
		HTTP, HTTPS;

		public static MODE find(String modeString) {
			if (modeString != null) {
				for (MODE m : values()) {
					if (m.name().equalsIgnoreCase(modeString)) {
						return m;
					}
				}
			}

			return HTTP;
		}
	}

	@MultipartConfig(maxFileSize = Long.MAX_VALUE)
	public static class JemoHTTPServlet extends HttpServlet {

		private JemoWebSocketServlet wsHandler = null;

		@Override
		protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			String connectionHeader = request.getHeader("Connection");
			if (connectionHeader != null && connectionHeader.equalsIgnoreCase("Upgrade")) {
				//TODO: we should add the web socket implementation handlers to the module interface and implement the pass through here.
				//this is a key missing feature here so we should implement it.
				if (wsHandler == null) {
					wsHandler = new JemoWebSocketServlet();
				}
				wsHandler.service(request, response);
			} else {
				//we need to process a normal http request. this will involve asking the plugin manager for an adequet module to handle this request.
				try {
					AbstractJemo jemoServer = JEMO_SERVER_REFERENCE_MAP.get(getInitParameter("server_reference_id"));
					jemoServer.getPluginManager().process(request, response);
				} catch (Throwable ex) {
					throw new ServletException(ex);
				}
			}
		}

		@Override
		protected long getLastModified(HttpServletRequest request) {
			return System.currentTimeMillis();
		}
	}

	private static class JemoWebSocket extends WebSocketAdapter {
		private JemoModule module = null;

		@Override
		public void onWebSocketText(String message) {
			super.onWebSocketText(message);
		}

		@Override
		public void onWebSocketError(Throwable cause) {
			Jemo.log(Level.WARNING, "WebSocket Channel Communication Error [%s]", JemoError.toString(cause));
			super.onWebSocketError(cause);
		}

		@Override
		public void onWebSocketConnect(Session session) {
			//session.getUpgradeRequest().getRequestURI()
			super.onWebSocketConnect(session); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void onWebSocketClose(int statusCode, String reason) {
			module = null;
			super.onWebSocketClose(statusCode, reason); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void onWebSocketBinary(byte[] payload, int offset, int len) {
			super.onWebSocketBinary(payload, offset, len); //To change body of generated methods, choose Tools | Templates.
		}

	}

	private static class JemoWebSocketServlet extends WebSocketServlet {

		@Override
		public void configure(WebSocketServletFactory wsFactory) {
			wsFactory.register(JemoWebSocket.class);
		}
	}

	private Server jettyServer = null;
	private final String SERVER_ID = UUID.randomUUID().toString();

	public JemoHTTPConnector(int httpsPort, int httpPort, AbstractJemo jemoServer) throws Exception {
		this.jemoServer = jemoServer;
		JEMO_SERVER_REFERENCE_MAP.put(SERVER_ID, jemoServer);
		Log.setLog(new Logger() {

			boolean isDebug = false;

			@Override
			public String getName() {
				return "Jemo";
			}

			public void log(Level level, String string, Object... os) {
				String arrStr = Arrays.asList(os).toString();
				jemoServer.LOG(string.replaceAll("\\{\\}", "") + " " + arrStr.substring(1, arrStr.length() - 1), level);
			}

			public void log(Level level, Throwable thrwbl) {
				jemoServer.LOG(level, "%s", JemoError.newInstance(thrwbl).toString());
			}

			public void log(Level level, String string, Throwable thrwbl) {
				jemoServer.LOG(level, "\"%s\" - %s", string, JemoError.newInstance(thrwbl).toString());
			}

			@Override
			public void warn(String string, Object... os) {
				log(Level.WARNING, string, os);
			}

			@Override
			public void warn(Throwable thrwbl) {
				log(Level.WARNING, thrwbl);
			}

			@Override
			public void warn(String string, Throwable thrwbl) {
				log(Level.WARNING, string, thrwbl);
			}

			@Override
			public void info(String string, Object... os) {
				log(Level.INFO, string, os);
			}

			@Override
			public void info(Throwable thrwbl) {
				log(Level.INFO, thrwbl);
			}

			@Override
			public void info(String string, Throwable thrwbl) {
				log(Level.INFO, string, thrwbl);
			}

			@Override
			public boolean isDebugEnabled() {
				return this.isDebug;
			}

			@Override
			public void setDebugEnabled(boolean bln) {
				this.isDebug = bln;
			}

			@Override
			public void debug(String string, Object... os) {
				if (isDebugEnabled()) log(Level.FINEST, string, os);
			}

			@Override
			public void debug(String string, long l) {
				jemoServer.LOG(string + " {" + l + "}", Level.FINEST);
			}

			@Override
			public void debug(Throwable thrwbl) {
				log(Level.FINEST, thrwbl);
			}

			@Override
			public void debug(String string, Throwable thrwbl) {
				log(Level.FINEST, string, thrwbl);
			}

			@Override
			public Logger getLogger(String string) {
				return this;
			}

			@Override
			public void ignore(Throwable thrwbl) {
			}
		});

		HttpConfiguration http_config = new HttpConfiguration();
		http_config.setSecureScheme("https");
		http_config.setSecurePort(httpsPort);
		http_config.setOutputBufferSize(32768);
		http_config.setRequestHeaderSize(8192);
		http_config.setResponseHeaderSize(8192);
		http_config.setSendServerVersion(false);
		http_config.setSendDateHeader(false);

		jettyServer = new Server(httpPort); //the http port will always be an increment of the https port.

		ServletHandler ccHttpHandler = new ServletHandler() {
			private final MultipartConfigElement MULTI_PART_CONFIG = new MultipartConfigElement(System.getProperty("java.io.tmpdir"));

			@Override
			public void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
				if (request.getContentType() != null && request.getContentType().startsWith("multipart/form-data")) {
					baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, MULTI_PART_CONFIG);
				}
				super.doHandle(target, baseRequest, request, response); //To change body of generated methods, choose Tools | Templates.
			}
		};
		jettyServer.setHandler(ccHttpHandler);

		ccHttpHandler.addServletWithMapping(JemoHTTPServlet.class, "/").setInitParameter("server_reference_id", SERVER_ID);

		SslContextFactory sslContextFactory = new SslContextFactory(true);
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(Jemo.class.getResourceAsStream("/jetty.jks"), "cloudreach".toCharArray());
		sslContextFactory.setKeyStore(ks);
		sslContextFactory.setKeyStorePassword("cloudreach");

		HttpConfiguration https_config = new HttpConfiguration(http_config);
		https_config.addCustomizer(new SecureRequestCustomizer());
		ServerConnector sslConnector = new ServerConnector(jettyServer,
				new SslConnectionFactory(sslContextFactory, "http/1.1"), new HttpConnectionFactory(https_config));
		sslConnector.setPort(httpsPort);
		jettyServer.addConnector(sslConnector);

		jettyServer.start();

		jemoServer.LOG("HTTP CONNECTOR STARTED HTTP(" + (httpPort) + ") HTTPS(" + (httpsPort) + ")", Level.INFO);
	}

	public void stop() throws Exception {
		jettyServer.stop();
		JEMO_SERVER_REFERENCE_MAP.remove(SERVER_ID);
		jemoServer = null;
	}
}
