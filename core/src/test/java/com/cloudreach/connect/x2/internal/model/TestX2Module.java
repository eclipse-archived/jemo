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

import com.cloudreach.connect.x2.api.Module;
import com.cloudreach.connect.x2.sys.CCPluginManager;
import com.cloudreach.x2.X2BaseTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestX2Module extends X2BaseTest {
	
	private static class TestModule implements Module {
		@Override
		public void construct(Logger logger, String name, int id, double version) {}

		@Override
		public String getBasePath() {
			return "/test";
		}

		@Override
		public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {}
	}
	
	@Test
	public void testClose() {
		Map<String,ScheduledFuture> testWatchdogList = new HashMap<>();
		X2Module mod = new X2Module(new TestModule(), new ModuleMetaData(60001, 1.0, TestModule.class.getSimpleName(), x2server.getPluginManager().getModuleLogger(60001, 1.0, TestModule.class))) {
			@Override
			public synchronized void close() {
				super.close(); //To change body of generated methods, choose Tools | Templates.
				testWatchdogList.putAll(watchdogList);
			}
		};
		mod.close();
		Assert.assertTrue(testWatchdogList.isEmpty());
	}
	
	@Test
	public void test_implementsWeb() {
		Assert.assertTrue(X2Module.implementsWeb(TestModule.class));
	}
}
