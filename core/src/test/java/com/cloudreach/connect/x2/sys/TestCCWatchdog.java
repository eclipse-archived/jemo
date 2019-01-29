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
package com.cloudreach.connect.x2.sys;

import com.cloudreach.connect.x2.internal.model.CloudProvider;
import com.cloudreach.x2.X2BaseTest;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestCCWatchdog {
	@Test
	public void testRun() {
		try {
			ArrayList<String> logMessage = new ArrayList<>();
			CCWatchdog watchdog = new CCWatchdog(new X2BaseTest.TestX2Server("UUID_"+UUID.randomUUID().toString(),"TEST",8080,"") {
					@Override
					public void LOG(Level logLevel, String message, Object... args) {
						logMessage.add(message);
					}
				});
			CloudProvider.defineCustomeRuntime(new X2BaseTest.MockRuntime() {
				@Override
				public void watchdog(String location, String instanceId, String instanceQueueUrl) {
					throw new RuntimeException("Error for now reason");
				}
			});
			watchdog.run();
			Assert.assertEquals("[CC][Watchdog] Error running watchdog: %s",logMessage.iterator().next());
		}finally {
			CloudProvider.defineCustomeRuntime(null);
		}
	}
}
