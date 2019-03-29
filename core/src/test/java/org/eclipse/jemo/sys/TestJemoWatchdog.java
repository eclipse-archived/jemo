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

import org.eclipse.jemo.internal.model.CloudProvider;
import org.eclipse.jemo.runtime.MemoryRuntime;
import org.eclipse.jemo.JemoBaseTest;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;
import org.junit.Assert;
import org.junit.Test;

import static org.eclipse.jemo.api.JemoParameter.CLOUD;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestJemoWatchdog {
	@Test
	public void testRun() {
		System.setProperty(CLOUD.label(),"MEMORY");
		try {
			ArrayList<String> logMessages = new ArrayList<>();
			JemoWatchdog watchdog = new JemoWatchdog(new JemoBaseTest.TestJemoServer("UUID_"+UUID.randomUUID().toString(),"TEST",8080,"") {
					@Override
					public void LOG(Level logLevel, String message, Object... args) {
						logMessages.add(message);
					}
				});
			CloudProvider.defineCustomeRuntime(new MemoryRuntime() {
				@Override
				public void watchdog(String location, String instanceId, String instanceQueueUrl) {
					throw new RuntimeException("Error for now reason");
				}
			});
			watchdog.run();
			Assert.assertEquals("[Jemo][Watchdog] Error running watchdog: %s", logMessages.get(logMessages.size()-1));
		}finally {
			CloudProvider.defineCustomeRuntime(null);
		}
	}
}
