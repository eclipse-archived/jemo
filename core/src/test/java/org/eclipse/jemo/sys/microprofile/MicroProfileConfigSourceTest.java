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

package org.eclipse.jemo.sys.microprofile;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jemo.JemoGSMTest;
import org.eclipse.jemo.api.FixedModule;
import org.eclipse.jemo.sys.JemoClassLoader;
import org.junit.Test;

public class MicroProfileConfigSourceTest extends JemoGSMTest {
	
	static AtomicReference<MicroProfileConfigSource> config = new AtomicReference<>();
	
	/**
	 * initial setup, we need a Jemo module which implements the processFixed handler.
	 */
	public static class FixedTestModule implements FixedModule {

		@Override
		public void processFixed(String location, String instanceId) throws Throwable {
			config.set(new MicroProfileConfigSource((JemoClassLoader)getClass().getClassLoader()));
		}
		
	}
	
	/**
	 * this test will create a module with a META-INF/microprofile-config.properties file inside of
	 * it, build a jar with both the module file and this resource file.
	 * 
	 * The module inside of it's processFixed method will create an instance of the MicroProfileConfigSource
	 * and we will expect to be able to access the properties in the file through the created source
	 * so we can validate them.
	 */
	@Test
	public void testConstructor() {
		uploadPlugin(91000, 1.0, FixedTestModule.class.getSimpleName(), 
				FixedTestModule.class, );
	}
}
