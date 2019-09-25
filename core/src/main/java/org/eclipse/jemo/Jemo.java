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
package org.eclipse.jemo;

import org.eclipse.jemo.api.JemoParameter;
import org.eclipse.jemo.internal.model.JemoMessage;
import org.eclipse.jemo.sys.JemoHTTPConnector;
import org.eclipse.jemo.sys.JemoPluginManager;
import org.eclipse.jemo.sys.JemoQueueListener;
import org.eclipse.jemo.sys.internal.Util;

import java.util.Properties;
import java.util.logging.Level;


/**
 * this is the base of the new Jemo microkernel
 * <p>
 * each Jemo instance will also have a location, this may be the name of a customer site,
 * or the name of a cloud provider, each location will have its own global work queue so that work requests
 * can be directed towards specific locations.
 *
 * @author christopher stura
 */
public class Jemo extends AbstractJemo {

    //we will need to do away with a lot of these static variables to allow our test to run but we still want to keep this streamlined
    //version for single instance Jemo executions.
    public static AbstractJemo SERVER_INSTANCE = new Jemo();

    /**
     * variables to set after server startup
     **/
    public static String INSTANCE_ID = null;
    public static String INSTANCE_QUEUE_URL = null;
    public static String GLOBAL_QUEUE_URL = null;
    public static String LOCATION_QUEUE_URL = null;
    public static String HOSTNAME = null;
    public static JemoPluginManager pluginManager = null;
    public static JemoHTTPConnector httpServer = null;
    public static String JEMO_VERSION;

    public static final JemoQueueListener getInstanceQueueListener() {
        return SERVER_INSTANCE.sys_getInstanceQueueListener();
    }

    public static void main(String[] args) throws Throwable {
        SERVER_INSTANCE.start();
        INSTANCE_ID = SERVER_INSTANCE.getINSTANCE_ID();
        INSTANCE_QUEUE_URL = SERVER_INSTANCE.getINSTANCE_QUEUE_URL();
        GLOBAL_QUEUE_URL = SERVER_INSTANCE.getGLOBAL_QUEUE_URL();
        LOCATION_QUEUE_URL = SERVER_INSTANCE.getLOCATION_QUEUE_URL();
        HOSTNAME = SERVER_INSTANCE.getHOSTNAME();
        //pluginManager = SERVER_INSTANCE.getPluginManager(); //we cannot grab a reference to the plugin manager here because we may have not yet setup to the plugin manager
        httpServer = SERVER_INSTANCE.getHttpServer();

        final Properties properties = new Properties();
        properties.load(Jemo.class.getClassLoader().getResourceAsStream("pom.properties"));
        JEMO_VERSION = properties.getProperty("jemo.pom.version");
    }

    public static final void processMessage(JemoMessage msg) throws Throwable {
        SERVER_INSTANCE.sys_processMessage(msg);
    }

    public static final boolean IS_CLOUD_LOCATION(String location) {
        return SERVER_INSTANCE.sys_IS_CLOUD_LOCATION(location);
    }

    private Jemo() {
        super(System.getProperty(JemoParameter.LOCATION.label(), (System.getProperty("aws.accessKeyId") == null ? "AWS" : "HEROKU")),
                Integer.parseInt(Util.readParameterFromJvmOrEnv(JemoParameter.HTTPS_PORT.label(), "443")),
                Integer.parseInt(Util.readParameterFromJvmOrEnv(JemoParameter.HTTP_PORT.label(), "80")),
                System.getProperty(JemoParameter.MODULE_WHITELIST.label(), ""), System.getProperty(JemoParameter.MODULE_BLACKLIST.label(), ""),
                Long.parseLong(System.getProperty(JemoParameter.QUEUE_POLL_TIME.label(), "20000")),
                Boolean.parseBoolean(System.getProperty(JemoParameter.LOCATION_TYPE.label(), "false")), System.getProperty(JemoParameter.LOG_LOCAL.label()) != null,
                System.getProperty(JemoParameter.LOG_OUTPUT.label()), Level.parse(System.getProperty(JemoParameter.LOG_LEVEL.label(), "INFO")), "JEMOUUID_V2");
    }

    public static final void log(String message, Level logLevel) {
        SERVER_INSTANCE.LOG(logLevel, message);
    }

    public static final void log(Level logLevel, String message, Object... args) {
        SERVER_INSTANCE.LOG(logLevel, message, args);
    }
}
