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
package com.cloudreach.connect.x2.internal.model;

import com.cloudreach.connect.x2.sys.X2ClassLoader;
import com.cloudreach.connect.x2.sys.internal.Util;
import java.io.ByteArrayOutputStream;
import java.util.logging.Level;

/**
 * this enumeration will help to identify what cloud provider we are running on
 * based on this key we will use different infrastructure components in the runtime.
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public enum CloudProvider {
	AWS("com.cloudreach.connect.x2.internal.model.AmazonAWSRuntime",null), AZURE("com.cloudreach.x2.runtime.azure.MicrosoftAzureRuntime","azure-x2-runtime.jar"),
	MEMORY("com.cloudreach.connect.x2.runtime.MemoryRuntime", null);
	
	private static CloudRuntime defaultRuntime = null;
	
	CloudRuntime runtime = null;
	String runtimeClass = null;
	String implementation = null;
	
	CloudProvider(String runtimeClass,String implementation) {
		this.runtimeClass = runtimeClass;
		this.implementation = implementation;
	}
	
	public static CloudProvider getInstance() {
		CloudProvider result = CloudProvider.AWS;
		for(CloudProvider provider : CloudProvider.values()) {
			if(System.getProperty("com.cloudreach.x2.cloud",AWS.name()).equalsIgnoreCase(provider.name())) {
				result = provider;
				break;
			}
		}
		
		return result; //default to AWS
	}
	
	private synchronized void initializeRuntime() {
		Util.B(null, x -> {
			if(implementation != null) {
				//we can avoid initializing this if we need to
				ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
				Util.stream(byteOut, getClass().getResourceAsStream("/"+implementation));
				X2ClassLoader cl = new X2ClassLoader(runtimeClass, byteOut.toByteArray());
				Class cloudRuntime = cl.loadClass(runtimeClass);
				runtime = CloudRuntime.class.cast(cloudRuntime.newInstance());
			} else {
				Class cloudRuntime = Class.forName(runtimeClass);
				runtime = CloudRuntime.class.cast(cloudRuntime.newInstance());
			}
		});
	}
	
	public CloudRuntime getRuntime() {
		if(defaultRuntime == null) {
			if(this.runtime == null) {
				//we need to initialize the runtime
				initializeRuntime();
			}
			return this.runtime;
		}
		return defaultRuntime;
	}
	
	public static final void defineCustomeRuntime(CloudRuntime runtime) {
		CloudProvider.defaultRuntime = runtime;
	}
}
