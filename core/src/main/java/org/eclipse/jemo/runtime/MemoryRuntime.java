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
package org.eclipse.jemo.runtime;

import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.internal.model.*;
import org.eclipse.jemo.sys.JemoRuntimeSetup;
import org.eclipse.jemo.sys.ClusterParams;
import org.eclipse.jemo.sys.internal.TerraformJob;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.jemo.api.JemoParameter;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.eclipse.jemo.sys.internal.Util.readParameterFromJvmOrEnv;
import static java.util.Arrays.asList;

/**
 * this is the implementation of a runtime which is entirely modelled inside of system memory.
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class MemoryRuntime implements CloudRuntime {
    private static final Logger LOG = Logger.getLogger(MemoryRuntime.class.getSimpleName());
    public static final String USER = "csp_memory_user";
    public static final String PASSWORD = "csp_memory_password";

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

	private static MessageQueueSystem QUEUE_SYSTEM = new MessageQueueSystem(); //the queue system will be global for all instances of Jemo running in this memory space.
	private static NoSQLDatabase NOSQL_DATABASE = new NoSQLDatabase();
	private static ObjectStorage OBJECT_STORAGE = new ObjectStorage();
	private final String DEFAULT_STORAGE_CATEGORY = "$$_DEFAULT_$$";
	private final String DEFAULT_STORAGE_PATH = "$$_SYS_STORAGE_$$";
	private final String MODULE_STORAGE_PATH = "SYS_MODULES";
    private String user, password;

    public MemoryRuntime() {
        user = System.getProperty(USER);
        password = System.getProperty(PASSWORD);
    }

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
	public Set<String> listPlugins() {
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
			.filter(q -> location == null || q.getName().startsWith("JEMO-"+location))
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
				Util.B(null, x -> processor.processMessage(Util.fromJSONString(JemoMessage.class, m.getData())));
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
			.filter(r -> asList(pkList).contains(r.getId()))
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
		asList(data).stream()
			.forEach(d -> {
				Util.B(null, x -> table.setRow(new NoSQLDatabase.NoSQLDataRow(d.getId(), Util.toJSONString(d))));
			});
	}

	@Override
	public void deleteNoSQL(String tableName, SystemDBObject... data) {
		NoSQLDatabase.NoSQLTable table = NOSQL_DATABASE.getTableByName(tableName);
		asList(data).stream()
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
	public ValidationResult validatePermissions() {
        // No genuine validation takes place.
        // Instead, this returns success if the selected csp is "MEMORY", otherwise it returns failure.
        // It is important to return failure when the CLOUD parameter is not set, otherwise Jemo will return the MemoryRuntime when no
        // valid user is found for the genuine CSPs, instead of forwarding the use to the setup UI.
		return "MEMORY".equals(readParameterFromJvmOrEnv(JemoParameter.CLOUD.label())) ?
				ValidationResult.SUCCESS : new ValidationResult(Collections.singletonList("Validation failed"));
	}

    @Override
    public ValidationResult validateCredentials(Map<String, String> credentials) {
        if (user.equals(credentials.get(USER)) && password.equals(credentials.get(PASSWORD))) {
            return ValidationResult.SUCCESS;
        } else {
            return new ValidationResult(asList("Invalid Credentials"));
        }
    }

    @Override
    public void updateCredentials(Map<String, String> credentials) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void start(AbstractJemo jemoServer) {
        //Nothing to do.
    }

	@Override
	public void setRegion(String regionCode) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public String readInstanceTag(String key) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public List<RegionInfo> getRegions() {
		return null;
	}

	@Override
	public void resetLogConsoleHandler(Handler handler) {
    	// Nothing to do.
	}

	@Override
	public List<String> getExistingNetworks() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public List<String> getCustomerManagedPolicies() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public ValidationResult validatePolicy(String policyName) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public JemoRuntimeSetup.ClusterCreationResponse createCluster(JemoRuntimeSetup.SetupParams setupParams, StringBuilder builder) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Path createClusterTerraformTemplates(JemoRuntimeSetup.SetupParams setupParams) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public List<InstallProperty> getInstallProperties() {
		return null;
	}

	@Override
	public void setInstallProperties(Map<String, String> properties) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public JemoRuntimeSetup.TerraformJobResponse install(String region, StringBuilder builder) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

    @Override
    public Map<String, String> getCredentialsFromTerraformResult(TerraformJob.TerraformResult terraformResult) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

	@Override
	public AdminUserCreationInstructions getAdminUserCreationInstructions() {
		return null;
	}

	@Override
    public Path createInstallTerraformTemplates(String region) throws IOException {
		throw new UnsupportedOperationException("Not supported yet.");
    }

	@Override
	public ClusterParams getClusterParameters() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public JemoRuntimeSetup.TerraformJobResponse deleteInstallResources(StringBuilder builder) throws IOException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public JemoRuntimeSetup.TerraformJobResponse deleteClusterResources(StringBuilder builder) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void deleteKubernetesResources(StringBuilder builder) throws IOException {

	}

	@Override
	public String getCspLabel() {
		return "memory";
	}

	@Override
	public void removeModule(String pluginFile) {
		remove(MODULE_STORAGE_PATH,pluginFile);
	}

	@Override
	public void removePluginFiles(String pluginJarFileName) {
		remove(MODULE_STORAGE_PATH, pluginJarFileName);
	}

	@Override
	public String isCliInstalled() {
		return null;
	}

}
