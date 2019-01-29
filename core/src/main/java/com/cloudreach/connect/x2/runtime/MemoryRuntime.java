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
package com.cloudreach.connect.x2.runtime;

import com.cloudreach.connect.x2.internal.model.CCMessage;
import com.cloudreach.connect.x2.internal.model.CloudBlob;
import com.cloudreach.connect.x2.internal.model.CloudLogEvent;
import com.cloudreach.connect.x2.internal.model.CloudQueueProcessor;
import com.cloudreach.connect.x2.internal.model.CloudRuntime;
import com.cloudreach.connect.x2.internal.model.ModuleConfiguration;
import com.cloudreach.connect.x2.internal.model.ModuleConfigurationOperation;
import com.cloudreach.connect.x2.internal.model.QueueDoesNotExistException;
import com.cloudreach.connect.x2.internal.model.SystemDBObject;
import com.cloudreach.connect.x2.sys.internal.Util;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * this is the implementation of a runtime which is entirely modelled inside of system memory.
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class MemoryRuntime implements CloudRuntime {

	private static class MessageQueue {
		protected static class Message {
			private final String ID = UUID.randomUUID().toString();
			private final String data;
			
			protected Message(String data) {
				this.data = data;
			}

			public String getID() {
				return ID;
			}

			public String getData() {
				return data;
			}
		}
		
		private final String ID;
		private final String name;
		private final List<Message> messages = new CopyOnWriteArrayList<>();
		private final AtomicInteger total = new AtomicInteger(0);
		private final AtomicInteger retrieved = new AtomicInteger(0);
		public final AtomicInteger processed = new AtomicInteger(0);
		
		protected MessageQueue(String name) {
			this.name = name;
			this.ID = "/"+UUID.randomUUID().toString()+"/"+name;
		}

		public String getName() {
			return name;
		}

		public String getID() {
			return ID;
		}
		
		public synchronized String sendMessage(String data) {
			Message msg = new Message(data);
			messages.add(msg);
			//System.out.println(String.format("QUEUE [%s] - message added total messages to process are: %d total added to queue %d retrieved %d processed %d", getName(), messages.size(), total.addAndGet(1), retrieved.get(), processed.get()));
			return msg.getID();
		}
		
		public synchronized List<Message> retrieveMessages(int quantity) {
			List<Message> result = messages.stream().limit(quantity).collect(Collectors.toList());
			messages.removeIf(m -> result.stream().anyMatch(r -> r.getID().equals(m.getID())));
			/*if(!result.isEmpty()) {
				System.out.println(String.format("QUEUE [%s] - message retrieved total messages to process are: %d total added to queue %d retrieved %d processed %d", getName(), messages.size(), total.get(), retrieved.addAndGet(result.size()), processed.get()));
			}*/
			return result;
		}
	}
	
	private static class MessageQueueSystem {
		private final List<MessageQueue> queueList = new ArrayList<>();
		
		protected synchronized MessageQueue createQueue(String queueName) {
			MessageQueue queue = queueList.stream().filter(q -> q.getName().equals(queueName)).findAny().orElse(null);
			if(queue == null) {
				queue = new MessageQueue(queueName);
				queueList.add(queue);
			}
			
			return queue;
		}
		
		protected synchronized void deleteQueue(String queueId) {
			queueList.removeIf(q -> q.getID().equals(queueId));
		}
		
		protected MessageQueue getQueue(String queueId) {
			return queueList.stream().filter(q -> q.getID().equals(queueId)).findAny().orElse(null);
		}
		
		protected MessageQueue getQueueByName(String queueName) {
			return queueList.stream().filter(q -> q.getName().equals(queueName)).findAny().orElse(null);
		}
		
		protected List<MessageQueue> listQueues() {
			return queueList;
		}
	}
	
	private static class NoSQLDatabase {
		private static class NoSQLDataRow {
			private final String id;
			private String data;
			
			protected NoSQLDataRow(String id,String data) {
				this.id = id;
				this.data = data;
			}

			public String getId() {
				return id;
			}

			public String getData() {
				return data;
			}

			public void setData(String data) {
				this.data = data;
			}
		}
		
		protected static class NoSQLTable {
			private final String name;
			private final List<NoSQLDataRow> rows = new ArrayList<>();
			
			protected NoSQLTable(String name) {
				this.name = name;
			}

			public String getName() {
				return name;
			}
			
			public synchronized NoSQLDataRow getRow(String id) {
				return rows.stream().filter(r -> r.getId().equals(id)).findAny().orElse(null);
			}
			
			public synchronized void setRow(NoSQLDataRow row) {
				NoSQLDataRow r = getRow(row.getId());
				if(r == null) {
					rows.add(row);
				} else {
					r.setData(row.getData());
				}
			}
			
			public synchronized void deleteRow(String id) {
				rows.removeIf(r -> r.getId().equals(id));
			}
		}
		
		private final List<NoSQLTable> tables = new ArrayList<>();
		
		protected synchronized NoSQLTable getTableByName(String tableName) {
			return tables.stream().filter(t -> t.getName().equals(tableName)).findAny().orElse(null);
		}
		
		protected synchronized NoSQLTable createTable(String tableName) {
			NoSQLTable table = getTableByName(tableName);
			if(table == null) {
				table = new NoSQLTable(tableName);
				tables.add(table);
			}
			
			return table;
		}
		
		protected synchronized void dropTable(String tableName) {
			tables.removeIf(t -> t.getName().equals(tableName));
		}
	}
	
	private static class ObjectStorage {
		private final Map<String,Object> data = new ConcurrentHashMap<>();
		
		public void put(String key,Object obj) {
			data.put(key,obj);
		}
		
		public <T extends Object> T get(Class<T> type,String key) {
			return data.containsKey(key) ? type.cast(data.get(key)) : null;
		}
		
		public void remove(String key) {
			data.remove(key);
		}
	}

	private static MessageQueueSystem QUEUE_SYSTEM = new MessageQueueSystem(); //the queue system will be global for all instances of X2 running in this memory space.
	private static NoSQLDatabase NOSQL_DATABASE = new NoSQLDatabase();
	private static ObjectStorage OBJECT_STORAGE = new ObjectStorage();
	private final String DEFAULT_STORAGE_CATEGORY = "$$_DEFAULT_$$";
	private final String DEFAULT_STORAGE_PATH = "$$_SYS_STORAGE_$$";
	private final String MODULE_STORAGE_PATH = "SYS_MODULES";
	
	public void reset() {
		QUEUE_SYSTEM = new MessageQueueSystem();
		NOSQL_DATABASE = new NoSQLDatabase();
		OBJECT_STORAGE = new ObjectStorage();
	}
	
	@Override
	public String defineQueue(String queueName) {
		return QUEUE_SYSTEM.createQueue(queueName).getID();
	}

	@Override
	public void storeModuleList(String moduleJar, List<String> moduleList) throws Throwable {
		store(DEFAULT_STORAGE_CATEGORY,moduleJar+".modulelist",moduleList);
	}

	@Override
	public List<String> getModuleList(String moduleJar) throws Throwable {
		return retrieve(DEFAULT_STORAGE_CATEGORY, moduleJar+".modulelist", List.class);
	}

	@Override
	public CloudBlob getModule(String moduleJar) throws IOException {
		return read(CloudBlob.class, MODULE_STORAGE_PATH, moduleJar);
	}

	@Override
	public Long getModuleInstallDate(String moduleJar) throws IOException {
		return retrieve(moduleJar+".installdate", Long.class);
	}

	@Override
	public void setModuleInstallDate(String moduleJar, long installDate) throws IOException {
		store(moduleJar+".installdate",installDate);
	}

	@Override
	public void log(List<CloudLogEvent> eventList) {} //writing logs to memory makes no sense.

	@Override
	public Set<String> listModules() {
		return readAll(CloudBlob.class, MODULE_STORAGE_PATH).map(cb -> cb.getKey())
			.collect(Collectors.toSet());
	}

	@Override
	public void uploadModule(String pluginFile, byte[] pluginBytes) {
		uploadModule(pluginFile, new ByteArrayInputStream(pluginBytes), pluginBytes.length);
	}

	@Override
	public void uploadModule(String pluginFile, InputStream in, long moduleSize) {
		CloudBlob blob = new CloudBlob(pluginFile, System.currentTimeMillis(), moduleSize, in);
		Util.B(null, x -> blob.getData());
		write(MODULE_STORAGE_PATH, pluginFile, blob);
	}

	@Override
	public void deleteQueue(String queueId) {
		QUEUE_SYSTEM.deleteQueue(queueId);
	}

	@Override
	public String sendMessage(String queueId, String jsonMessage) {
		return QUEUE_SYSTEM.getQueue(queueId).sendMessage(jsonMessage);
	}

	@Override
	public String getQueueId(String queueName) {
		MessageQueue queue = QUEUE_SYSTEM.getQueueByName(queueName);
		return queue == null ? null : queue.getID();
	}

	@Override
	public List<String> listQueueIds(String location, boolean includeWorkQueues) {
		return QUEUE_SYSTEM.listQueues().stream()
			.filter(q -> location == null || q.getName().startsWith("CC-"+location))
			.filter(q -> includeWorkQueues || !q.getName().endsWith("-WORK-QUEUE"))
			.map(q -> q.getID())
			.collect(Collectors.toList());
	}

	@Override
	public String getQueueName(String queueId) {
		MessageQueue queue = QUEUE_SYSTEM.getQueue(queueId);
		return queue != null ? queue.getName() : null;
	}

	@Override
	public synchronized int pollQueue(String queueId, CloudQueueProcessor processor) throws QueueDoesNotExistException {
		MessageQueue q = QUEUE_SYSTEM.getQueue(queueId);
		if(q != null) {
			List<MessageQueue.Message> msgList = q.retrieveMessages(10); //retrieve 10 messages at a time.
			msgList.forEach(m -> {
				Util.B(null, x -> processor.processMessage(Util.fromJSONString(CCMessage.class, m.getData())));
				/*System.out.println(String.format("QUEUE [%s] - message processed total messages to process are: %d total added to queue %d retrieved %d processed %d", q.getName(), q.messages.size(), q.total.get(), 
					q.retrieved.get(), q.processed.addAndGet(1)));*/
			});
			return msgList.size();
		} else {
			throw new QueueDoesNotExistException("The queueId: "+queueId+" does not exist on the system");
		}
	}

	@Override
	public boolean hasNoSQLTable(String tableName) {
		return NOSQL_DATABASE.getTableByName(tableName) != null;
	}

	@Override
	public void createNoSQLTable(String tableName) {
		NOSQL_DATABASE.createTable(tableName);
	}

	@Override
	public void dropNoSQLTable(String tableName) {
		NOSQL_DATABASE.dropTable(tableName);
	}

	@Override
	public <T> List<T> listNoSQL(String tableName, Class<T> objectType) {
		return NOSQL_DATABASE.getTableByName(tableName).rows.stream()
			.map(r -> Util.F(null, x -> Util.fromJSONString(objectType, r.getData())))
			.collect(Collectors.toList());
	}

	@Override
	public <T> List<T> queryNoSQL(String tableName, Class<T> objectType, String... pkList) {
		return NOSQL_DATABASE.getTableByName(tableName).rows.stream()
			.filter(r -> Arrays.asList(pkList).contains(r.getId()))
			.map(r -> Util.F(null, x -> Util.fromJSONString(objectType, r.getData())))
			.collect(Collectors.toList());
	}

	@Override
	public <T> T getNoSQL(String tableName, String id, Class<T> objectType) throws IOException {
		NoSQLDatabase.NoSQLDataRow row = NOSQL_DATABASE.getTableByName(tableName).getRow(id);
		if(row != null) {
			return Util.fromJSONString(objectType,row.getData());
		}
		
		return null;
	}

	@Override
	public void saveNoSQL(String tableName, SystemDBObject... data) {
		NoSQLDatabase.NoSQLTable table = NOSQL_DATABASE.getTableByName(tableName);
		Arrays.asList(data).stream()
			.forEach(d -> {
				Util.B(null, x -> table.setRow(new NoSQLDatabase.NoSQLDataRow(d.getId(), Util.toJSONString(d))));
			});
	}

	@Override
	public void deleteNoSQL(String tableName, SystemDBObject... data) {
		NoSQLDatabase.NoSQLTable table = NOSQL_DATABASE.getTableByName(tableName);
		Arrays.asList(data).stream()
			.forEach(d -> {
				table.deleteRow(d.getId());
			});
	}

	@Override
	public void watchdog(final String location,final String instanceId,final String instanceQueueUrl) {}

	@Override
	public void setModuleConfiguration(int pluginId, ModuleConfiguration config) {
		Map<String,String> currentConfig = getModuleConfiguration(pluginId).entrySet().stream()
			.filter(e -> !config.getParameters().stream()
				.filter(cfg -> cfg.getOperation() == ModuleConfigurationOperation.delete)
				.anyMatch(cfg -> cfg.getKey().equals(e.getKey()))
			).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
		currentConfig.putAll(config.getParameters().stream()
				.filter(cfg -> cfg.getOperation() == ModuleConfigurationOperation.upsert)
				.collect(Collectors.toMap(cfg -> cfg.getKey(), cfg -> cfg.getValue()))
		);
		store(String.valueOf(pluginId)+".moduleconfig",currentConfig);
	}

	@Override
	public Map<String, String> getModuleConfiguration(int pluginId) {
		Map<String, String> config = retrieve(String.valueOf(pluginId)+".moduleconfig", Map.class);
		return config == null ? new HashMap<>() : config;
	}

	@Override
	public void store(String key, Object data) {
		store(DEFAULT_STORAGE_CATEGORY,key,data);
	}

	@Override
	public <T> T retrieve(String key, Class<T> objType) {
		return retrieve(DEFAULT_STORAGE_CATEGORY, key, objType);
	}

	@Override
	public void store(String category, String key, Object data) {
		write(category, DEFAULT_STORAGE_PATH, key, data);
	}

	@Override
	public <T> T retrieve(String category, String key, Class<T> objType) {
		return read(objType, category, DEFAULT_STORAGE_PATH, key);
	}

	@Override
	public void delete(String category, String key) {
		remove(category, DEFAULT_STORAGE_PATH, key);
	}

	@Override
	public String getDefaultCategory() {
		return DEFAULT_STORAGE_CATEGORY;
	}

	@Override
	public void write(String category, String path, String key, InputStream dataStream) {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		Util.B(null, x -> Util.stream(byteOut, dataStream));
		OBJECT_STORAGE.put(category+"/"+path+"/"+key, byteOut.toByteArray());
	}

	@Override
	public InputStream read(String category, String path, String key) {
		byte[] data = OBJECT_STORAGE.get(byte[].class, category+"/"+path+"/"+key);
		return data != null ? new ByteArrayInputStream(data) : null;
	}

	@Override
	public Stream<InputStream> readAll(String category, String path) {
		return OBJECT_STORAGE.data.entrySet().stream()
			.filter(e -> e.getKey().startsWith(category+"/"+path))
			.map(e -> new ByteArrayInputStream((byte[])e.getValue()));
	}

	@Override
	public void remove(String category, String path, String key) {
		OBJECT_STORAGE.remove(category+"/"+(path == null ? key : path+"/"+key));
	}

	@Override
	public void removeModule(String pluginFile) {
		remove(MODULE_STORAGE_PATH,pluginFile);
	}
}
