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
package com.cloudreach.connect.x2.api;

import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestModuleLimit {
	
	@Test
	public void test_MaxActiveBatchesPerInstance() {
		assertEquals(1,ModuleLimit.newInstance().setMaxActiveBatchesPerInstance(1).build().getMaxActiveBatchesPerInstance());
	}
	
	@Test
	public void test_MaxActiveBatchesPerLocation() {
		assertEquals(1,ModuleLimit.newInstance().setMaxActiveBatchesPerLocation(1).build().getMaxActiveBatchesPerLocation());
	}
	
	@Test
	public void test_MaxActiveBatchesPerGSM() {
		assertEquals(1,ModuleLimit.newInstance().setMaxActiveBatchesPerGSM(1).build().getMaxActiveBatchesPerGSM());
	}
	
	@Test
	public void test_MaxActiveEventsPerInstance() {
		assertEquals(1,ModuleLimit.newInstance().setMaxActiveEventsPerInstance(1).build().getMaxActiveEventsPerInstance());
	}
	
	@Test
	public void test_BatchLocations() {
		assertArrayEquals(new String[] {"TEST1","TEST2"},ModuleLimit.newInstance().setBatchLocations("TEST1","TEST2").build().getBatchLocations());
	}
	
	@Test
	public void test_BatchFrequency() {
		assertNotNull(ModuleLimit.newInstance().setBatchFrequency(Frequency.of(TimeUnit.DAYS, 1)).build().getBatchFrequency());
		assertEquals(1,ModuleLimit.newInstance().setBatchFrequency(Frequency.of(TimeUnit.DAYS, 1)).build().getBatchFrequency().getValue());
	}
}
