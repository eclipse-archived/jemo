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
package com.cloudreach.connect.x2;

import com.amazonaws.regions.Region;
import com.cloudreach.connect.x2.internal.model.AmazonAWSRuntime;
import com.cloudreach.connect.x2.internal.model.CCMessage;
import com.cloudreach.connect.x2.sys.CCHTTPConnector;
import com.cloudreach.connect.x2.sys.CCPluginManager;
import com.cloudreach.connect.x2.sys.CCQueueListener;
import java.util.Set;
import java.util.logging.Level;

/**
 * this is the base of the new Cloudreach Connect microkernel
 * 
 * each Cloudreach Connect instance will also have a location, this may be the name of a customer site,
 * or the name of a cloud provider, each location will have its own global work queue so that work requests
 * can be directed towards specific locations.
 * 
 * @author christopher stura
 */
public class CC extends AbstractX2 {
	
	//we will need to do away with a lot of these static variables to allow our test to run but we still want to keep this streamlined
	//version for single instance x2 executions.
	public static AbstractX2 SERVER_INSTANCE = new CC();
	
	/** variables to set after server startup **/
	public static String INSTANCE_ID = null;
	public static String INSTANCE_QUEUE_URL = null;
	public static String GLOBAL_QUEUE_URL = null;
	public static String LOCATION_QUEUE_URL = null;
	public static String GLOBAL_TOPIC_ARN = null;
	public static String LOCATION_TOPIC_ARN = null;
	public static String HOSTNAME = null;
	public static CCPluginManager pluginManager = null;
	public static CCHTTPConnector httpServer = null;
	
	public static final String LOCATION = SERVER_INSTANCE.getLOCATION();
	public static final Region AWSREGION = AmazonAWSRuntime.AWSREGION;
	public static final String LOCATION_QUEUE_NAME = SERVER_INSTANCE.getLOCATION_QUEUE_NAME();
	public static final String LOCATION_TOPIC_NAME = null;
	public static final int CC_HTTPS_PORT = SERVER_INSTANCE.getCC_HTTPS_PORT();
	public static final CCHTTPConnector.MODE CC_HTTP_MODE = SERVER_INSTANCE.getCC_HTTP_MODE();
	public static final Set<Integer> PLUGIN_WHITELIST = SERVER_INSTANCE.getPLUGIN_WHITELIST();
	public static final Set<Integer> PLUGIN_BLACKLIST = SERVER_INSTANCE.getPLUGIN_BLACKLIST();
	public static final long QUEUE_POLL_WAIT_TIME = SERVER_INSTANCE.getQUEUE_POLL_WAIT_TIME();
	public static final boolean IN_CLOUD_LOCATION = SERVER_INSTANCE.isIN_CLOUD_LOCATION();
	
	public static final CCQueueListener getInstanceQueueListener() {
		return SERVER_INSTANCE.sys_getInstanceQueueListener();
	}
	
	public static void main(String[] args) throws Throwable {
		SERVER_INSTANCE.start();
		INSTANCE_ID = SERVER_INSTANCE.getINSTANCE_ID();
		INSTANCE_QUEUE_URL = SERVER_INSTANCE.getINSTANCE_QUEUE_URL();
		GLOBAL_QUEUE_URL = SERVER_INSTANCE.getGLOBAL_QUEUE_URL();
		LOCATION_QUEUE_URL = SERVER_INSTANCE.getLOCATION_QUEUE_URL();
		HOSTNAME = SERVER_INSTANCE.getHOSTNAME();
		pluginManager = SERVER_INSTANCE.getPluginManager();
		httpServer = SERVER_INSTANCE.getHttpServer();
		
	}
	
	public static final void processMessage(CCMessage msg) throws Throwable {
		SERVER_INSTANCE.sys_processMessage(msg);
	}
	
	public static final boolean IS_CLOUD_LOCATION(String location) {
		return SERVER_INSTANCE.sys_IS_CLOUD_LOCATION(location);
	}
	
	private CC() {
		super(System.getProperty("cloudreach.connect.location", (System.getProperty("aws.accessKeyId") == null ? "AWS" : "HEROKU")),
					Integer.parseInt(System.getProperty("cloudreach.connect.http.port", "8080")),
					CCHTTPConnector.MODE.find(System.getProperty("cloudreach.connect.http.mode")),
					System.getProperty("cloudreach.connect.plugin.whitelist",""), System.getProperty("cloudreach.connect.plugin.blacklist",""),
					Long.parseLong(System.getProperty("cloudreach.connect.queue.polltime", "20000")),
					Boolean.parseBoolean(System.getProperty("cloudreach.connect.location.cloud","false")), System.getProperty("cloudreach.connect.logs") != null, 
					System.getProperty("cloudreach.connect.output"),Level.parse(System.getProperty("cloudreach.connect.log.level","INFO")),"X2UUID_V2");
	}
	
	public static final void log(String message,Level logLevel) {
		SERVER_INSTANCE.LOG(logLevel, message);
	}
	
	public static final void log(Level logLevel,String message,Object... args) {
		SERVER_INSTANCE.LOG(logLevel, message, args);
	}
}
