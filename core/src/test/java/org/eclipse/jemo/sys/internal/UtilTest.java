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
package org.eclipse.jemo.sys.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * This class will contain unit tests for the Util class used throughout the Jemo platform.
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class UtilTest {
	
	public UtilTest() {
	}
	
	@BeforeClass
	public static void setUpClass() {
	}
	
	@AfterClass
	public static void tearDownClass() {
	}

	@Test
	public void testDeleteDirectory() throws IOException {
		assertTrue(Util.deleteDirectory(null));
		//lets create a temporary file and call delete directory on it.
		assertTrue(Util.deleteDirectory(File.createTempFile("tmp", "dir")));
		//lets create a directory with two files in it.
		File testDir = new File(System.getProperty("java.io.tmpdir"),"test_dir");
		testDir.mkdir();
		File file1 = new File(testDir,"file1");
		try(FileOutputStream fout = new FileOutputStream(file1)) {
			fout.write(new byte[] {0,1,2});
		}
		File testNestedDir = new File(testDir,"nested");
		testNestedDir.mkdir();
		File file2 = new File(testNestedDir,"file2");
		try(FileOutputStream fout = new FileOutputStream(file1)) {
			fout.write(new byte[] {0,1,2});
		}
		assertTrue(Util.deleteDirectory(testDir));
		assertFalse(file1.exists());
		assertFalse(file2.exists());
	}
	
}
