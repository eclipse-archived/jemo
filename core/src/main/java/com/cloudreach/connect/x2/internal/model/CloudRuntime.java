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
package com.cloudreach.connect.x2.internal.model;

import com.cloudreach.connect.x2.sys.internal.Util;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public interface CloudRuntime {
	public String defineQueue(String queueName);
	public void storeModuleList(String moduleJar,List<String> moduleList) throws Throwable;
	public List<String> getModuleList(String moduleJar) throws Throwable;
	public CloudBlob getModule(String moduleJar) throws IOException;
	public Long getModuleInstallDate(String moduleJar) throws IOException;
	public void setModuleInstallDate(String moduleJar,long installDate) throws IOException;
	public void log(List<CloudLogEvent> eventList);
	public Set<String> listModules();
	public void uploadModule(String pluginFile,byte[] pluginBytes);
	public void uploadModule(String pluginFile,InputStream in, long moduleSize);
	public default void removeModule(String pluginFile) {
		remove(null, pluginFile);
	}
	
	public default String createInstanceQueue(final String location,final String instanceId) {
		return defineQueue("CC-"+location+"-"+instanceId);
	}
	public void deleteQueue(String queueId);
	public String sendMessage(String queueId,String jsonMessage);
	
	/**
	 * this method will return the corresponding id for the queueName which was passed,
	 * this method does not guarantee that the queue actually exists but will return an id that can be used for the other methods.
	 * for cloud vendors who cannot generate the id of the queue but instead need to look it up using the api's this method
	 * can return null.
	 * 
	 * @param queueName the name of the queue to fetch the id for.
	 * @return the unique id representing the queue.
	 */
	public String getQueueId(String queueName);
	public default List<String> listQueueIds(String location) {
		return listQueueIds(location, false);
	}
	
	/**
	 * this method will return the name of a queue given it's cloud native identifier
	 * @param queueId the id of the queue as identified by the cloud provider
	 * @return the name of the queue
	 */
	public String getQueueName(String queueId);
	
	public List<String> listQueueIds(String location,boolean includeWorkQueues);
	public int pollQueue(String queueId,CloudQueueProcessor processor) throws QueueDoesNotExistException;
	public boolean hasNoSQLTable(String tableName);
	public void createNoSQLTable(String tableName);
	public void dropNoSQLTable(String tableName);
	public <T extends Object> List<T> listNoSQL(String tableName,Class<T> objectType);
	public <T extends Object> List<T> queryNoSQL(String tableName,Class<T> objectType,String... pkList);
	public <T extends Object> T getNoSQL(String tableName,String id,Class<T> objectType) throws IOException;
	public void saveNoSQL(String tableName,SystemDBObject... data);
	public void deleteNoSQL(String tableName,SystemDBObject... data);
	public void watchdog(final String location,final String instanceId,final String instanceQueueUrl);
	public void setModuleConfiguration(int pluginId,ModuleConfiguration config);
	public Map<String,String> getModuleConfiguration(int pluginId);
	public void store(String key,Object data);
	public <T extends Object> T retrieve(String key,Class<T> objType);
	public void store(String category,String key,Object data);
	public <T extends Object> T retrieve(String category,String key,Class<T> objType);
	public void delete(String category,String key);
	public String getDefaultCategory();
	
	//we added support to the runtime for data streaming to big data repositories.
	public default InputStream read(String path,String key) {
		return read(getDefaultCategory(),path,key);
	}
	
	public default <T extends Object> T read(Class<T> returnType,String path,String key) {
		return read(returnType,getDefaultCategory(),path,key);
	}
	
	public default <T extends Object> T read(Class<T> returnType,String category, String path, String key) {
		return Util.F(read(category,path,key), input -> input == null ? null : Util.fromJSONString(returnType, Util.toString(input)));
	}
	
	public default <T extends Object> Stream<T> readAll(Class<T> type,String path) {
		return readAll(type,getDefaultCategory(),path);
	}
	
	public default <T extends Object> Stream<T> readAll(Class<T> type,String category,String path) {
		return readAll(category, path).map(input -> Util.F(null, x -> Util.fromJSONString(type, Util.toString(input))));
	}
	
	public default void write(String path,String key,Object data) {
		write(getDefaultCategory(),path,key,data);
	}
	
	public default void write(String category,String path,String key,Object data) {
		Util.B(null, x -> write(category,path,key,new ByteArrayInputStream(Util.toJSONString(data).getBytes("UTF-8"))));
	}
	
	public default void write(String path,String key,InputStream dataStream) {
		write(getDefaultCategory(),path,key,dataStream);
	}
	
	public default void remove(String path,String key) {
		remove(getDefaultCategory(),path,key);
	}
	
	public void write(String category,String path,String key,InputStream dataStream);
	public InputStream read(String category,String path,String key);
	public Stream<InputStream> readAll(String category,String path);
	public void remove(String category,String path,String key);
}
