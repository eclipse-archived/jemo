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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.internal.model.*;
import org.eclipse.jemo.sys.JemoPluginManager.PluginManagerModule;
import org.eclipse.jemo.sys.auth.JemoUser;
import org.eclipse.jemo.sys.internal.Util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.eclipse.jemo.sys.JemoPluginManager.*;
import static org.eclipse.jemo.sys.JemoPluginManager.PluginManagerModule.respondWithJson;
import static org.eclipse.jemo.sys.internal.Util.readAllBytes;

public class JemoRuntimeAdmin {

    public static final String JEMO_ADMIN = "/jemo/admin";
    public static final String JEMO_PLUGINS = JEMO_ADMIN + "/plugins";
    private static final Pattern PLUGIN_VERSION_PATTERN = Pattern.compile(JEMO_PLUGINS + "/(\\d+)/(.*)");

    public static void processRequest(PluginManagerModule pluginManagerModule, JemoUser authUser, HttpServletRequest request, HttpServletResponse response) throws Throwable {
        switch (request.getMethod()) {
            case "GET":
                if (JEMO_PLUGINS.equals(request.getRequestURI())) {
                    getUploadedApps(response);
                } else {
                    loadFile(request.getRequestURI().replaceAll(JEMO_ADMIN, ""), response);
                }
                break;
            case "POST":
                switch (request.getRequestURI()) {
                    default:
                        response.sendError(404, "No functionality is mapped to this endpoint yet" + request.getRequestURI());
                }
                break;
            case "OPTIONS":
                response.addHeader("Access-Control-Allow-Origin", "*");
                response.setHeader("Access-Control-Allow-Methods", "GET,HEAD,OPTIONS,POST,PUT,PATCH,DELETE");
                response.setHeader("Access-Control-Allow-Headers", "Access-Control-Allow-Headers, Origin,Accept, X-Requested-With, Authorization, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
                break;
            case "DELETE":
                Matcher matcher;
                if ((matcher = PLUGIN_VERSION_PATTERN.matcher(request.getRequestURI())).find()) {
                    deletePluginVersion(pluginManagerModule, authUser, Integer.parseInt(matcher.group(1)), matcher.group(2), response);
                } else {
                    response.sendError(400);
                }
                break;
            case "PUT":
                response.sendError(404, "No functionality is mapped to this endpoint yet" + request.getRequestURI());
                break;
            case "PATCH":
                if ((matcher = PLUGIN_VERSION_PATTERN.matcher(request.getRequestURI())).find()) {
                    changeState(pluginManagerModule, authUser, Integer.parseInt(matcher.group(1)), matcher.group(2), request, response);
                }
                response.sendError(404, "No functionality is mapped to this endpoint yet" + request.getRequestURI());
                break;
            default:
                response.sendError(400);
        }
    }

    private static void getUploadedApps(HttpServletResponse response) throws IOException {
        final List<Plugin> plugins = readAppMetadataFromDB().stream()
                .map(Plugin::new)
                .sorted()
                .collect(Collectors.toList());
        respondWithJson(200, response, plugins);
    }

    private static void deletePluginVersion(PluginManagerModule pluginManagerModule, JemoUser authUser, int pluginId, String version, HttpServletResponse response) throws Throwable {
        final boolean isDeleted = pluginManagerModule.deletePlugin(pluginId, Double.parseDouble(version), authUser);
        final int statusCode = isDeleted ? 204 : 404;
        respondWithJson(statusCode, response, null);
    }

    private static void changeState(PluginManagerModule pluginManagerModule, JemoUser authUser, int pluginId, String version, HttpServletRequest request, HttpServletResponse response) throws Throwable {
        final JemoApplicationMetaData newPartialState = Jemo.fromJSONString(JemoApplicationMetaData.class, Util.toString(request.getInputStream()));
        final double versionAsDouble = Double.parseDouble(version);
        Optional<JemoApplicationMetaData> appMetaData = readAppMetadataFromDB().stream()
                .filter(app -> PLUGIN_ID(app.getId()) == pluginId && PLUGIN_VERSION(app.getId()) == versionAsDouble)
                .findFirst();

        if (!appMetaData.isPresent()) {
            respondWithJson(404, response, null);
        } else {
            if (appMetaData.get().isEnabled() != newPartialState.isEnabled()) {
                pluginManagerModule.changeState(appMetaData.get(), authUser);
                final JemoApplicationMetaData newMetaData = readAppMetadataFromDB().stream()
                        .filter(app -> PLUGIN_ID(app.getId()) == pluginId && PLUGIN_VERSION(app.getId()) == versionAsDouble)
                        .findFirst().get();

                respondWithJson(200, response, new Plugin(newMetaData));
            } else {
                respondWithJson(204, response, null);
            }
        }
    }

    /**
     * Forwards to plugins/index.html
     *
     * @param requestUri
     * @param response   the http servlet response object
     * @throws IOException
     */
    private static void loadFile(String requestUri, HttpServletResponse response) throws IOException {
        final String fileName = requestUri.isEmpty() || requestUri.equals("/") ? "/index.html" : requestUri;
        final InputStream in = Jemo.class.getResourceAsStream("/ui/admin" + fileName);
        Jemo.stream(response.getOutputStream(), new ByteArrayInputStream(readAllBytes(in)));
    }

    public static class Plugin implements Comparable {

        @JsonProperty
        private PluginInfo pluginInfo;

        @JsonProperty
        private JemoApplicationMetaData metaData;

        public Plugin() {
        }

        public Plugin(JemoApplicationMetaData metaData) {
            this.pluginInfo = new PluginInfo(metaData.getId());
            this.metaData = metaData;
        }

        @Override
        public int compareTo(Object o) {
            Plugin that = (Plugin)o;
            return this.pluginInfo.compareTo(that.pluginInfo);
        }

        public static class PluginInfo implements Comparable {

            @JsonProperty
            private int id;

            @JsonProperty
            private String name;

            @JsonProperty
            private String version;


            public PluginInfo() {
            }

            public PluginInfo(String pluginJarFileName) {
                this.id = PLUGIN_ID(pluginJarFileName);
                this.name = PLUGIN_NAME(pluginJarFileName);
                this.version = String.valueOf(PLUGIN_VERSION(pluginJarFileName));
            }

            @Override
            public int compareTo(Object o) {
                PluginInfo that = (PluginInfo)o;
                if (this.id != that.id) {
                    return this.id - that.id;
                } else {
                    return this.version.compareTo(that.version);
                }
            }
        }
    }

}
