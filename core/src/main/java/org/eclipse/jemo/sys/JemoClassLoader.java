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
package org.eclipse.jemo.sys;

import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.api.Module;
import org.eclipse.jemo.internal.model.JemoError;
import org.eclipse.jemo.internal.model.CloudBlob;
import org.eclipse.jemo.internal.model.CloudProvider;
import org.eclipse.jemo.sys.internal.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

/**
 * this class represents the main class loader for the Jemo aPaaS system
 * 
 * @author christopher stura
 */
public class JemoClassLoader extends URLClassLoader {
	
	private static final Map<Long,Set<String>> cacheUsageMap = new ConcurrentHashMap<>();
	private static volatile boolean cacheCleared = false;
	
	private static URL createTemporaryFile(String uniqueKey) {
		try {
			clearTemporaryFiles();
			return new URL("mem", "", -1, uniqueKey, new URLStreamHandler() {
				
				@Override
				protected URLConnection openConnection(URL u) throws IOException {
					return openConnection(u, null);
				}

				@Override
				protected URLConnection openConnection(URL u, Proxy p) throws IOException {
					return new URLConnection(u) {
						ByteArrayInputStream byteIn = new ByteArrayInputStream(new byte[] {0,0,0});
						
						@Override
						public void connect() throws IOException {}

						@Override
						public InputStream getInputStream() throws IOException {
							return byteIn;
						}
					};
				}
				
				
			});
		}catch(IOException ioEx) {
			throw new RuntimeException(ioEx);
		}
	}
	
	public static void clearTemporaryFiles() {
		if(!cacheCleared) {
			cacheCleared = true;
			/*Jemo.log(Level.INFO, "Clearing Stale File Cache");
			File tmpDir = new File(System.getProperty("java.io.tmpdir"));
			Arrays.asList(tmpDir.listFiles()).parallelStream().filter(f -> (f.getName().startsWith("tmp_") && f.getName().endsWith("jar")) || f.getName().endsWith("_cache"))
						.sequential().forEach(f -> {
							if(f.isDirectory()) {
								Jemo.log(Level.INFO, "Deleting Cache Directory %s [%s]", f.getName(), Jemo.deleteDirectory(f) ? "SUCCESS" : "FAILED");
							} else {
								f.delete();
							}
						});
			Jemo.log(Level.INFO, "File Cache Cleared");*/
		}
	}
	
	private static long CRC32(byte[] data) {
		final CRC32 crc32 = new CRC32();
		crc32.update(data);
		return crc32.getValue();
	}
	
	public static long CRC32(File file) throws IOException {
		final CRC32 crc32 = new CRC32();
		try(FileInputStream fin = new FileInputStream(file)) {
			byte[] buf = new byte[8192];
			int rb = 0;
			while((rb = fin.read(buf)) != -1) {
				crc32.update(buf, 0, rb);
			}
		}
		return crc32.getValue();
	}
	
	private static class AdjacentClassLoader {
		private JemoClassLoader classLoader;
		private final Map<String,Class> loadedClasses = new HashMap<>();
		private final boolean exclusivelyOwned;
		
		public AdjacentClassLoader(JemoClassLoader classLoader, boolean exclusivelyOwned) {
			this.classLoader = classLoader;
			this.exclusivelyOwned = exclusivelyOwned;
		}

		public boolean isExclusivelyOwned() {
			return exclusivelyOwned;
		}
		
		public synchronized Class loadClass(String name) throws ClassNotFoundException {
			if(loadedClasses.containsKey(name)) {
				return loadedClasses.get(name);
			}
			if(classLoader.isClassLoaded(name)) {
				return classLoader.loadClass(name);
			} else {
				Class cls = classLoader.findClass(name);
				loadedClasses.put(name, cls);
				return cls;
			}
		}
		
		public Set<String> getClassList() {
			return classLoader.getClassList();
		}
		
		public Set<String> getResourceList() {
			return classLoader.getResourceList();
		}
		
		public InputStream getResourceAsStream(String name) {
			return classLoader.getResourceAsStream(name, false);
		}
		
		public String getUniqueId() {
			return classLoader.uniqueKey;
		}
		
		public void close() {
			loadedClasses.clear();
			classLoader = null;
		}
	}
	
	private ClassLoader parentClassLoader = null;
	private String uniqueKey = null;
	private long crc32 = 0;
	private File cacheDirectory = null;
	private long createdDate = System.currentTimeMillis();
	private Map<Integer,AdjacentClassLoader> adjacentClassLoaderMap = new LinkedHashMap<>();
	private Set<String> loadedClasses = new ConcurrentSkipListSet<>();
	private volatile Set<String> localClassList = null;
	
	public JemoClassLoader(String uniqueKey, byte[] data) {
		this(uniqueKey, data, JemoClassLoader.class.getClassLoader());
	}
	
	public JemoClassLoader(String uniqueKey, byte[] data, ClassLoader parentClassLoader) {
		this(uniqueKey, new ByteArrayInputStream(data), parentClassLoader);
	}
	
	public JemoClassLoader(String uniqueKey, String jarModule, long moduleCrc, ClassLoader parentClassLoader) throws IOException {
		super(new URL[]{ createTemporaryFile(uniqueKey) }, parentClassLoader);
		this.uniqueKey = uniqueKey;
		this.parentClassLoader = parentClassLoader;
		
		cacheDirectory = new File(System.getProperty("java.io.tmpdir"),String.valueOf(moduleCrc)+"_cache");
		boolean downloadAndCache = (!cacheDirectory.exists() || !new File(cacheDirectory, "create_date").exists());
		if(cacheDirectory.exists()) {
			Set<String> cachedClassList = JemoPluginManager.getClassList(jarModule, null, false);
			Set<String> dirClassList = getClassList();
			//the dir class list should contain all of the classes inside of the cached class list.
			if(!dirClassList.containsAll(cachedClassList)) {
				downloadAndCache = true;
			}
		}
		if(downloadAndCache) {
			cacheDirectory.mkdirs();
			//ok we need to download and unpack
			Jemo.log(Level.INFO, "[%s] has not been downloaded, and will now be downloaded and unpacked.", jarModule);
			CloudBlob blob = CloudProvider.getInstance().getRuntime().getModule(jarModule);
			int retry = 5;
			do {
				try(FileOutputStream fout = new FileOutputStream(new File(cacheDirectory, "create_date"))) {
					fout.write(String.valueOf(blob.getCreatedDate()).getBytes("UTF-8"));
					retry = 0;
				} catch(FileNotFoundException fnfEx) {
					try { Thread.sleep(500); } catch(InterruptedException irrEx) {}//wait 500 ms.
					cacheDirectory.mkdirs();
				}
				retry--;
			}while(retry > 0);
			this.createdDate = blob.getCreatedDate();
			initClassLoader(blob.getDataStream());
		} else {
			this.crc32 = moduleCrc;
			try(FileInputStream fin = new FileInputStream(new File(cacheDirectory, "create_date"))) {
				this.createdDate = Long.parseLong(Jemo.toString(fin));
			}
			//if we do this we still need to indicate that we are using the cache directory.
			(cacheUsageMap.putIfAbsent(crc32, new CopyOnWriteArraySet<>()) == null ? cacheUsageMap.get(crc32) : cacheUsageMap.get(crc32)).add(this.uniqueKey);
		}
	}
	
	public JemoClassLoader(String uniqueKey, InputStream data, ClassLoader parentClassLoader) {
		super(new URL[]{ createTemporaryFile(uniqueKey) }, parentClassLoader);
		this.uniqueKey = uniqueKey;
		this.parentClassLoader = parentClassLoader;
		
		initClassLoader(data);
	}
	
	private synchronized void initClassLoader(InputStream data) {
		boolean cacheDirCreated = false;
		InputStream mainDataStream = data;
		File cacheFile = null;
		//we need to re-calculate the crc because it is not accurate.
		try {
			cacheFile = File.createTempFile(uniqueKey, ".jar_cache");
			try(FileOutputStream fout = new FileOutputStream(cacheFile)) {
				byte[] buf = new byte[8192];
				int rb = 0;
				final CRC32 crc = new CRC32();
				while((rb = data.read(buf)) != -1) {
					crc.update(buf, 0, rb);
					fout.write(buf, 0, rb);
				}
				this.crc32 = crc.getValue();
			}
			mainDataStream = new FileInputStream(cacheFile);
		}catch(IOException ioEx) {
			throw new RuntimeException(ioEx);
		}finally {
			try {
				data.close();
			}catch(IOException ioEx) {}
		}

		//check if a directory with this CRC32 value already exists in our temporary files.
		cacheDirectory = new File(System.getProperty("java.io.tmpdir"),String.valueOf(this.crc32)+"_cache");
		if(!cacheDirectory.exists()) {
			cacheDirectory.mkdirs();
			cacheDirCreated = true;
		}
		Set<String> classLoaderKeySet = cacheUsageMap.get(this.crc32);
		if(classLoaderKeySet == null) {
			classLoaderKeySet = new CopyOnWriteArraySet<>();
			cacheUsageMap.put(this.crc32,classLoaderKeySet);
		}
		classLoaderKeySet.add(uniqueKey);
		
		try(JarInputStream jarIn = new JarInputStream(mainDataStream, false)) {
			JarEntry jEntry;
			while((jEntry = jarIn.getNextJarEntry()) != null) {
				if(!jEntry.isDirectory()) {
					File f = new File(cacheDirectory,jEntry.getName());
					File pf = f.getParentFile();
					if(!pf.exists()) {
						pf.mkdirs();
					}
					try(FileOutputStream fout = new FileOutputStream(f)) {
						Jemo.stream(fout, jarIn, false);
					}
				}
			}
		}catch(IOException ioEx) {
			throw new RuntimeException(ioEx);
		}
		if(cacheFile != null) {
			cacheFile.delete();
		}
	}
	
	public final Set<String> getClassList() {
		return getClassList(cacheDirectory,cacheDirectory, true);
	}
	
	public final Set<String> getLocalClassList() {
		return getClassList(cacheDirectory,cacheDirectory, false);
	}
	
	public final Set<String> getAdjacentClassList() {
		return adjacentClassLoaderMap.keySet()
				.stream().sorted()
				.flatMap(id -> adjacentClassLoaderMap.get(id).getClassList().stream())
				.collect(Collectors.toSet());
	}
	
	public final Set<String> getResourceList() {
		return getResourceList(cacheDirectory, cacheDirectory);
	}
	
	private Set<String> getResourceList(File parentDirectory,File cacheDirectory) {
		HashSet<String> result = new HashSet<>();
		Arrays.asList(parentDirectory.listFiles()).stream().forEach(f -> {
			if(f.isDirectory()) {
				result.addAll(getResourceList(f,cacheDirectory));
			} else if(!f.getName().endsWith(".class")) {
				result.add(f.getPath().substring(cacheDirectory.getPath().length())); //this is not exactly right as we need to remove the begining of the path name.
			}
		});
		//now also add classes from any adjacent class loaders to this one.
		if(!adjacentClassLoaderMap.isEmpty()) {
			result.addAll(adjacentClassLoaderMap.keySet()
				.stream().sorted()
				.flatMap(id -> adjacentClassLoaderMap.get(id).getResourceList().stream())
				.collect(Collectors.toSet()));
		}
		return result;
	}
	
	/**
	 * this method will return the full list of classes available to the class loader.
	 * @return 
	 */
	private Set<String> getClassList(File parentDirectory,File cacheDirectory, boolean includeAdjacent) {
		Set<String> result = new HashSet<>();
		if(localClassList == null) {
			localClassList = buildClassListFromFileSystem(parentDirectory, cacheDirectory);
		}
		result.addAll(localClassList);
		//now also add classes from any adjacent class loaders to this one.
		if(!adjacentClassLoaderMap.isEmpty() && includeAdjacent) {
			result.addAll(getAdjacentClassList());
		}
		return result;
	}
	
	private synchronized Set<String> buildClassListFromFileSystem(File parentDirectory,File cacheDirectory) {
		Set<String> result = new HashSet<>();
		Arrays.asList(parentDirectory.listFiles()).stream().forEach(f -> {
			if(f.isDirectory()) {
				result.addAll(buildClassListFromFileSystem(f,cacheDirectory));
			} else if(f.getName().endsWith(".class")) {
				result.add(f.getPath().replace(File.separatorChar, '.').substring(cacheDirectory.getPath().replace(File.separatorChar, '.').length()+1,f.getPath().replace(File.separatorChar, '.').lastIndexOf('.')));
			}
		});
		return result;
	}

	public long getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(long createdDate) {
		this.createdDate = createdDate;
	}

	public long getCRC32() {
		return crc32;
	}
	
	private byte[] getFromJar(String resource) {
		File f = new File(cacheDirectory,resource);
		if(f.exists()) {
			ByteArrayOutputStream byteOut;
			try {
				try(FileInputStream fin = new FileInputStream(f)) {
					byteOut = new ByteArrayOutputStream((int)f.length());
					Jemo.stream(byteOut, fin, false);
				}

				return byteOut.toByteArray();
			}catch(IOException ioEx) {
				Jemo.log(Level.WARNING, "[Jemo][JemoClassLoader][%s][%s] could not read from file: %s", f.getPath(), resource, JemoError.toString(ioEx));
			}
		}
		
		return null; 
	}

	@Override
	public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		return sys_loadClass(name, resolve);
	}
	
	private synchronized Class<?> sys_loadClass(String name,boolean resolve) throws ClassNotFoundException {
		//this whole thing needs to be synchronised
		Class<?> cls = findLoadedClass(name); //if the class had not already been loaded
		if(cls == null) {
			//so by default the load class method will ask for the class from the parent class loader which will cause a lookup to happen on the parent
			//before it happens on the current version of the class loader. of course we only want to use the parent first if the class we are looking
			//for is not contained within an adjacent class loader of the parent.
			if(parentClassLoader instanceof JemoClassLoader && ((JemoClassLoader)parentClassLoader).isProvidedByAdjacentClassLoader(name) && getLocalClassList().contains(name)) {
				cls = findClass(name); //we don't need to worry about it existing because we have checked the file exists locally already.
				if(resolve) {
					resolveClass(cls);
				}
			} else {
				return super.loadClass(name, resolve); //To change body of generated methods, choose Tools | Templates.
			}
		}
		
		return cls;
	}
	
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		Class<?> cls = defineClassLocally(name);
		if(cls == null) { //if we can't find it locally then look to adjacent loaders
			for(AdjacentClassLoader adjLdr : adjacentClassLoaderMap.keySet().stream().sorted()
				.map(k -> adjacentClassLoaderMap.get(k))
				.toArray(AdjacentClassLoader[]::new)) {
				try {
					return adjLdr.loadClass(name); //keep this in the domain of the adjacent loader
				}catch(ClassNotFoundException clsNfEx) {}
			}
			throw new ClassNotFoundException(String.format("The Class %s could not be found",name));
		}
		return cls;
	}

	/**
	 * this method will check if the class passed in by name is provided by one of the adjacent class loaders
	 * or if it is instead provided by the main class loader instance.
	 * 
	 * it is important to note that first we will see if the parent contains this class if it does this method will return false
	 * regardless of whether this class is also provided by an adjacent loader.
	 * 
	 * @param name the name of the class to locate.
	 * @return true if provided by an adjacent loader and false if it does not
	 */
	protected boolean isProvidedByAdjacentClassLoader(String name) {
		if(getLocalClassList().contains(name)) {
			return false;
		}
		
		return getAdjacentClassList().contains(name);
	}
	
	protected Class<?> loadClassFromParent(String name) {
		try {
			return getParent().loadClass(name); //first check if our parent has a reference to this class.
		}catch(ClassNotFoundException clsNfEx) {}
		
		return null;
	}
	
	/**
	 * this method will attempt to define the class identified by name using the local class loader instance. 
	 * if the class specified by name cannot be found in this class loader without using its parent or adjacent loaders
	 * then null will be returned otherwise a reference to the defined class reference will be returned.
	 * 
	 * @param name the name of the class to lookup and define
	 * @return a reference to the class which was just defined or null if that class could not be found in the local class loader.
	 */
	protected Class<?> defineClassLocally(String name) {
		byte[] classBytes = getFromJar(name.replace('.', '/')+".class"); //else check within ourselves.
		if(classBytes != null) {
			//we should also track the classes that we have already loaded by direct calls to this class loader.
			Class cls = defineClass(name, classBytes, 0, classBytes.length);
			loadedClasses.add(name);
			return cls;
		}
		
		return null;
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return getResourceAsStream(name, true);
	}
	
	public InputStream getResourceAsStream(String name, boolean parentLookup) {
		byte[] resourceBytes = getFromJar(name);
		InputStream res = parentLookup  ? (resourceBytes == null ? getParent().getResourceAsStream(name) : new ByteArrayInputStream(resourceBytes)) : resourceBytes == null ? null : new ByteArrayInputStream(resourceBytes);
		if(res == null) {
			//lets try and find it in our adjacent class loaders.
			for(AdjacentClassLoader adjLdr : adjacentClassLoaderMap.keySet().stream().sorted()
				.map(k -> adjacentClassLoaderMap.get(k))
				.toArray(AdjacentClassLoader[]::new)) {
				res = adjLdr.getResourceAsStream(name);
				if(res != null) {
					return res;
				}
			}
		}
		
		return res;
	}
	
	public boolean isClassLoaded(String className) {
		return loadedClasses.contains(className);
	}

	@Override
	public void close() throws IOException {
		loadedClasses.clear();
		adjacentClassLoaderMap.clear();
		if(localClassList != null) {
			localClassList.clear();
		}
		try {
			clearFileCache();
			this.uniqueKey = null;
		}finally {
			this.parentClassLoader = null;
			super.close(); //To change body of generated methods, choose Tools | Templates.
		}
	}

	@Override
	@SuppressWarnings("FinalizeDeclaration")
	protected void finalize() throws Throwable {
		try {
			close();
		}finally { //make sure this is always called to cleanup.
			super.finalize();
		}
	}	
	
	private synchronized void clearFileCache() throws IOException {
		Set<String> classLoaderKeySet = cacheUsageMap.get(this.crc32);
		if(classLoaderKeySet != null) {
			classLoaderKeySet.remove(this.uniqueKey);
			if(classLoaderKeySet.isEmpty()) {
				Jemo.deleteDirectory(cacheDirectory);
				Jemo.log(Level.FINE, "[%s][%s][%s] cache directory removed successfully.", getClass().getSimpleName(), this.uniqueKey, this.cacheDirectory.getPath());
			}
		}
		//now adjacent class loaders could be shared with other class loaders as well, so we should only clear the cache on those
		//which we own exlusively.
		for(AdjacentClassLoader ldr : adjacentClassLoaderMap.values().stream()
			.filter(ldr -> ldr.isExclusivelyOwned())
			.toArray(AdjacentClassLoader[]::new)) {
			ldr.close();
		}
		adjacentClassLoaderMap.clear();
	}

	/**
	 * this method will associate or embed a class loader directly within this loader. this means that whenever
	 * an attempt to resolve a class using this class loader is made if the class to resolve cannot be found it will attempt
	 * to resolve the resource using one of the associated class loaders. Since the association is made by reference it also means
	 * that you can create a graph of dependencies and share resources between class loader instances as well as dynamically change
	 * resolution entities.
	 * 
	 * @param priority where in the priority stack this associate class loader will be placed.
	 * @param classLoader the class loader instance to use for resolution.
	 * @param transferOwnership whether to transfer ownership of this class loader exclusively to this parent. (this will cause the class loader to be unloaded along with the parent).
	 */
	public synchronized void associateClassLoader(int priority, JemoClassLoader classLoader, boolean transferOwnership) throws IOException {
		AdjacentClassLoader currentLoader = adjacentClassLoaderMap.get(priority);
		if(currentLoader != null) {
			currentLoader.close();
			currentLoader = null; //remove reference
			System.gc(); //request class unloading.
		}
		adjacentClassLoaderMap.put(priority, new AdjacentClassLoader(classLoader, transferOwnership));
		Util.log(Level.INFO, "[%s][%s] The class loader %s has been added as an adjacent class loader", getClass().getSimpleName(), uniqueKey, classLoader.uniqueKey);
	}
	
	/**
	 * this method will access the plugin manager running in the current instance and return an instance
	 * of the module referenced by it's implementation class name. This will then allow you to use other utilities
	 * to invoke operations directly on the module instance running in another container.
	 * 
	 * please note: this method will return null if the module has not been loaded in the current instance or if the security
	 * settings for the module which you wish to access to not allow it.
	 * 
	 * @param moduleId the id of the module where to look for the class specified
	 * @param moduleClassName the name of the class implementing the module
	 * @return the instance of the module being run in the current application server instance
	 */
	public Module lookupModule(final int moduleId,final String moduleClassName) {
		return Util.F(null, p -> {
			Object jemomodule = Util.runStaticMethod(Object.class, "org.eclipse.jemo.sys.JemoPluginManager", "getModuleByClassName", moduleId, moduleClassName);
			
			return Util.runMethod(jemomodule, Module.class, "getModule");
		});
	}
}
