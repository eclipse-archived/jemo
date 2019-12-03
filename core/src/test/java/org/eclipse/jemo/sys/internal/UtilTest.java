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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarInputStream;

import javax.annotation.Priority;
import javax.enterprise.inject.Any;
import javax.inject.Inject;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.steadystate.css.util.ThrowCssExceptionErrorHandler;

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
	
	@Test
	public void testCreateJar() throws Throwable {
		final String entryName = "/META-INF/microprofile-config.properties";
		final Properties propertyFile = new Properties();
		final ByteArrayOutputStream propertyFileOut = new ByteArrayOutputStream();
		propertyFile.setProperty("test.key", "value");
		propertyFile.store(propertyFileOut, "Test Property File");
		
		final ByteArrayOutputStream jarOut = new ByteArrayOutputStream();
		Util.createJar(jarOut, new JarEntry(entryName, new ByteArrayInputStream(propertyFileOut.toByteArray())));
		
		//now that we have created the jar we need to verify that our entry is there 
		//and that the data in the jar entry is correct
		JarInputStream jarIn = new JarInputStream(new ByteArrayInputStream(jarOut.toByteArray()), false);
		java.util.jar.JarEntry entry;
		List<JarEntry> entryList = new ArrayList<>();
		while((entry = jarIn.getNextJarEntry()) != null) {
			ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			Util.stream(byteOut, jarIn, false);
			entryList.add(new JarEntry(entry.getName(), new ByteArrayInputStream(byteOut.toByteArray())));
		}
		assertEquals(1,entryList.size());
		assertTrue(entryList.stream().anyMatch(e -> e.getEntryName().equals(entryName)));
		byte[] entryBytes = Util.toByteArray(entryList.get(0).getEntryData());
		assertArrayEquals(propertyFileOut.toByteArray(), entryBytes);
		final Properties loadedProperties = new Properties();
		loadedProperties.load(new ByteArrayInputStream(entryBytes));
		assertTrue(loadedProperties.containsKey("test.key"));
		assertEquals("value", loadedProperties.get("test.key"));
	}
	
	private static class ByteClassLoader extends ClassLoader {
		ByteClassLoader(ClassLoader parent) {
			super(parent);
		}
		
		public Class getClassFromBytes(String className, byte[] classBytes) throws Throwable {
			return defineClass(className, classBytes, 0, classBytes.length);
		}
	}
	
	@Test
	public void testGetBytesFromClass() throws Throwable {
		byte[] classBytes = Util.getBytesFromClass(Util.class);
		Class classFromBytes = new ByteClassLoader(getClass().getClassLoader()).getClassFromBytes(Util.class.getName(), classBytes);
		assertEquals(Util.class.getName(),  classFromBytes.getName());
		assertEquals(Util.class.getMethods().length, classFromBytes.getMethods().length);
	}
	
	private static class ClassWithAnnotations {
		
		@Inject
		@Any
		private String var1 = "a";
		
		@Inject
		private String var2 = "b";
		
	}
	
	@Test
	public void test_listFieldsWithAnnotations() throws Throwable {
		List<Field> fields = Util.listFieldsWithAnnotations(new ClassWithAnnotations(), Inject.class, Any.class);
		assertEquals(1, fields.size());
	}
}
