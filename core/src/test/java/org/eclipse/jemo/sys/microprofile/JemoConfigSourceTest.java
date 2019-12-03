/*
********************************************************************************
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

import static java.util.AbstractMap.SimpleEntry;
import java.util.Map;

import org.eclipse.jemo.sys.internal.Util;
import org.junit.Test;

/**
 * The purpose of this test file is to ensure that the class org.eclipse.jemo.sys.microprofile.JemoConfigSource
 * obtains 100% code coverage from a unit test standpoint.
 * 
 * @author Christopher Stura "cstura@gmail.com"
 */
public class JemoConfigSourceTest {

	@Test
	public void testGetProperties() {
		JemoConfigSource src = new JemoConfigSource(Util.MAP(
				new SimpleEntry<>("test1", "value1"),
				new SimpleEntry<>("test2", "value2")
			));
		assertEquals("value2", src.getProperties().get("test2"));
		assertEquals("value1", src.getProperties().get("test1"));
	}
	
	@Test
	public void testGetValue() {
		JemoConfigSource src = new JemoConfigSource(Util.MAP(
				new SimpleEntry<>("test1", "value1"),
				new SimpleEntry<>("test2", "value2")
			));
		assertEquals("value1", src.getValue("test1"));
		assertEquals("value2", src.getValue("test2"));
	}
	
	@Test
	public void testGetName() {
		JemoConfigSource src = new JemoConfigSource(Util.MAP(
				new SimpleEntry<>("test1", "value1"),
				new SimpleEntry<>("test2", "value2")
			));
		assertEquals(JemoConfigSource.class.getSimpleName(), src.getName());
	}
	
	@Test
	public void testGetOrdinal() {
		JemoConfigSource src = new JemoConfigSource(Util.MAP(
				new SimpleEntry<>("test1", "value1"),
				new SimpleEntry<>("test2", "value2")
			));
		assertEquals(100, src.getOrdinal());
	}
}
