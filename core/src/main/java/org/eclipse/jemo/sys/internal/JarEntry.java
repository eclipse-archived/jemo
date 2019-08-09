package org.eclipse.jemo.sys.internal;

import java.io.InputStream;

public class JarEntry {
	final String entryName;
	final InputStream entryData;
	
	public JarEntry(String entryName,InputStream entryData) {
		this.entryName = entryName;
		this.entryData = entryData;
	}

	public String getEntryName() {
		return entryName;
	}

	public InputStream getEntryData() {
		return entryData;
	}
}
