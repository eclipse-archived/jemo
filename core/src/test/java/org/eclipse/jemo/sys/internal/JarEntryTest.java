package org.eclipse.jemo.sys.internal;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;

public class JarEntryTest {

	@Test
	public void testJarEntry() throws IOException {
		JarEntry entry = new JarEntry("test", new ByteArrayInputStream(new byte[] {0,1,2}));
		assertNotNull(entry);
		assertEquals("test",entry.getEntryName());
		assertNotNull(entry.getEntryData());
		assertArrayEquals(new byte[] {0,1,2}, Util.toByteArray(entry.getEntryData()));
	}
	
	@Test
	public void testJarEntryClass() throws IOException {
		final byte[] classBytes = Util.getBytesFromClass(JarEntryTest.class);
		final JarEntry classEntry = new JarEntry(JarEntryTest.class);
		assertEquals(JarEntryTest.class.getName().replace('.', '/') + ".class", classEntry.getEntryName());
		assertNotNull(classEntry.getEntryData());
		assertArrayEquals(classBytes, Util.toByteArray(classEntry.getEntryData()));
	}
	
	@Test
	public void testFromClass() throws IOException {
		final byte[] classBytes = Util.getBytesFromClass(JarEntryTest.class);
		final JarEntry classEntry = JarEntry.fromClass(JarEntryTest.class);
		assertEquals(JarEntryTest.class.getName().replace('.', '/') + ".class", classEntry.getEntryName());
		assertNotNull(classEntry.getEntryData());
		assertArrayEquals(classBytes, Util.toByteArray(classEntry.getEntryData()));
	}
	
	@Test
	public void testFromProperties() throws IOException {
		Properties props = new Properties();
		props.setProperty("test", "value");
		final JarEntry propEntry = JarEntry.fromProperties("entry", props);
		assertEquals("entry", propEntry.getEntryName());
		
		Properties loadedProps = new Properties();
		loadedProps.load(propEntry.getEntryData());
		assertTrue(loadedProps.containsKey("test"));
		assertEquals("value", loadedProps.getProperty("test"));
	}
}
