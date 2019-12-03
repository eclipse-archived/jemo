package org.eclipse.jemo.sys.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class JarEntry {
	final String entryName;
	final InputStream entryData;
	
	public JarEntry(String entryName,InputStream entryData) {
		this.entryName = entryName;
		this.entryData = entryData;
	}
	
	//to make things easier we should also be able to create a JarEntry from a class file.
	public JarEntry(Class<?> cls) throws IOException {
		this.entryName = cls.getName().replace('.', '/') + ".class";
		this.entryData = new ByteArrayInputStream(Util.getBytesFromClass(cls));
	}

	public String getEntryName() {
		return entryName;
	}

	public InputStream getEntryData() {
		return entryData;
	}
	
	public static JarEntry fromClass(Class cls) throws IOException {
		return new JarEntry(cls);
	}
	
	public static JarEntry fromBytes(String entryName, byte[] data) {
		return new JarEntry(entryName, new ByteArrayInputStream(data));
	}
	
	public static JarEntry fromProperties(String entryName,Properties properties) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		properties.store(out, "");
		return fromBytes(entryName,out.toByteArray());
	}
}
