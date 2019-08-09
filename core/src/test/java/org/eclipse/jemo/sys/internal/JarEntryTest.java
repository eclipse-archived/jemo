package org.eclipse.jemo.sys.internal;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class JarEntryTest {

	@Test
	public void testJarEntry() throws IOException {
		JarEntry entry = new JarEntry("test", new ByteArrayInputStream(new byte[] {0,1,2}));
		assertNotNull(entry);
		assertEquals("test",entry.getEntryName());
		assertNotNull(entry.getEntryData());
		assertArrayEquals(new byte[] {0,1,2}, Util.toByteArray(entry.getEntryData()));
	}
}
