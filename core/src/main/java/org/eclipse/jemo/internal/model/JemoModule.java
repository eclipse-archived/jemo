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
package org.eclipse.jemo.internal.model;

import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.api.Module;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author christopherstura
 */
public class JemoModule {
	
	private Module module = null;
	private ModuleMetaData metaData = null;
	protected final Map<String,ScheduledFuture> watchdogList = new ConcurrentHashMap<>();
	
	public JemoModule(Module module, ModuleMetaData metaData) {
		this.module = module;
		this.metaData = metaData;
	}

	public Module getModule() {
		return module;
	}

	public ModuleMetaData getMetaData() {
		return metaData;
	}
	
	public boolean implementsBatch() {
		return implementsBatch(getModule().getClass());
	}
	
	public boolean implementsEvent() {
		return implementsEvent(getModule().getClass());
	}
	
	public boolean implementsFixed() {
		return implementsFixed(getModule().getClass());
	}
	
	public void addWatchdog(String watchdogId,ScheduledFuture watchdog) {
		watchdogList.put(watchdogId,watchdog);
	}
	
	public synchronized void shutdownWatchdog(String watchdogId) {
		if(watchdogList.containsKey(watchdogId)) {
			watchdogList.get(watchdogId).cancel(false);
			watchdogList.remove(watchdogId);
		}
	}
	
	public synchronized void close() {
		watchdogList.keySet().stream().forEach(id -> shutdownWatchdog(id));
		watchdogList.clear();
	}
	
	public static boolean implementsBatch(final Class<? extends Module> moduleClass) {
		return Optional.ofNullable(Jemo.executeFailsafe(x -> {
			return !moduleClass.getMethod("processBatch", String.class, boolean.class).getDeclaringClass().getName().equals(Module.class.getName());
		}, null)).orElse(false);
	}
	
	public static boolean implementsEvent(final Class<? extends Module> moduleClass) {
		return Optional.ofNullable(Jemo.executeFailsafe(x -> {
			return !moduleClass.getMethod("process", JemoMessage.class).getDeclaringClass().getName().equals(Module.class.getName());
		}, null)).orElse(false);
	}
	
	public static boolean implementsWeb(final Class<? extends Module> moduleClass) {
		return Optional.ofNullable(Jemo.executeFailsafe(x -> {
			if(!moduleClass.getMethod("process", HttpServletRequest.class, HttpServletResponse.class).getDeclaringClass().getName().equals(Module.class.getName())) {
				return !moduleClass.getMethod("getBasePath").getDeclaringClass().getName().equals(Module.class.getName());
			}
			
			return false;
		}, null)).orElse(false);
	}
	
	public static boolean implementsFixed(final Class<? extends Module> moduleClass) {
		return Optional.ofNullable(Jemo.executeFailsafe(x -> {
			return !moduleClass.getMethod("processFixed", String.class, String.class).getDeclaringClass().getName().equals(Module.class.getName());
		}, null)).orElse(false);
	}
}
