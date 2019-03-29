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
package org.eclipse.jemo.sys.auth;

import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.HttpServletRequestAdapter;
import org.eclipse.jemo.JemoBaseTest;
import org.eclipse.jemo.internal.model.CloudProvider;
import org.eclipse.jemo.internal.model.SystemDBObject;
import org.eclipse.jemo.runtime.MemoryRuntime;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.jemo.HttpServletResponseAdapter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.xml.ws.Holder;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * this test should cover the JemoAuthentication class to make sure all of the features
 * offered through that web service implementation are covered.
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestJemoAuthentication extends JemoBaseTest {
	
	public TestJemoAuthentication() throws Throwable { super(); }
	
	@Test
	public void testInit() throws Throwable {
		JemoAuthentication.init(jemoServer);
		//we should be able to pull the system administrator user after initialization
		Field adminUsernameField = JemoAuthentication.class.getDeclaredField("USER_ADMIN");
		adminUsernameField.setAccessible(true);
		JemoUser adminUser = JemoAuthentication.getUser((String)adminUsernameField.get(JemoAuthentication.class));
		assertNotNull(adminUser);
		
		//if we want to test this further we could mock the default runtime.
		MemoryRuntime runtime = new MemoryRuntime() {
			Map<String,List<SystemDBObject>> memDb = new HashMap<>();
			
			@Override
			public void createNoSQLTable(String tableName) {
				memDb.put(tableName, new ArrayList<>());
			}

			@Override
			public <T> List<T> listNoSQL(String tableName, Class<T> objectType) {
				return (List<T>)memDb.get(tableName);
			}

			@Override
			public boolean hasNoSQLTable(String tableName) {
				return memDb.containsKey(tableName);
			}			

			@Override
			public void saveNoSQL(String tableName, SystemDBObject... data) {
				memDb.get(tableName).addAll(Arrays.asList(data));
			}

			@Override
			public <T> T getNoSQL(String tableName, String id, Class<T> objectType) throws IOException {
				return objectType.cast(memDb.get(tableName).stream().filter(obj -> obj.getId().equals(id)).findAny().orElse(null));
			}			

			@Override
			public void dropNoSQLTable(String tableName) {
				memDb.remove(tableName);
			}
		};
		CloudProvider.defineCustomeRuntime(runtime);
		try {
			//now initialize since the moc runtime is only in memory it will always start off as blank
			/*Field f_created_groups_table = JemoAuthentication.class.getDeclaredField("created_groups_table");
			f_created_groups_table.setAccessible(true);
			f_created_groups_table.set(JemoAuthentication.class, false);

			Field f_created_users_table = JemoAuthentication.class.getDeclaredField("created_users_table");
			f_created_users_table.setAccessible(true);
			f_created_users_table.set(JemoAuthentication.class, false);*/

			JemoAuthentication.init(jemoServer);
			adminUser = JemoAuthentication.getUser((String)adminUsernameField.get(JemoAuthentication.class));
			assertNotNull(adminUser);
			
			//f_created_users_table.set(JemoAuthentication.class, false);
			
			Field f_TABLE_USERS = JemoAuthentication.class.getDeclaredField("TABLE_USERS");
			f_TABLE_USERS.setAccessible(true);
			runtime.dropNoSQLTable((String)f_TABLE_USERS.get(JemoAuthentication.class));
			
			JemoAuthentication.init(jemoServer);
			adminUser = JemoAuthentication.getUser((String)adminUsernameField.get(JemoAuthentication.class));
			assertNotNull(adminUser);
		}finally {
			CloudProvider.defineCustomeRuntime(null);
		}
	}
	
	@Test
	public void testProcessRequest() throws Throwable {
		//1. test a POST request with no authenticated user
		HttpServletRequestAdapter requestAdapter = new HttpServletRequestAdapter() {
			@Override
			public String getMethod() {
				return "POST";
			}

			@Override
			public String getRequestURI() {
				return "/jemo/authentication/user";
			}
		};
		Holder<Integer> responseStatusCode = new Holder<>(200);
		HttpServletResponseAdapter responseAdapter = new HttpServletResponseAdapter() {
			@Override
			public void setStatus(int i) {
				responseStatusCode.value = i;
			}
		};
		JemoAuthentication.processRequest(null, requestAdapter, responseAdapter);
		//we expect the status in the http response to be 401 access denied
		assertEquals(new Integer(401),responseStatusCode.value);
		responseAdapter.reset();
		//2. test an authenticated non admin user who will try and create a new user with various types of data.
		String groupAdminId = getAdminGroupId();
		assertNotNull(groupAdminId);
		JemoUser standardAuthUser = new JemoUser();
		standardAuthUser.setUsername("standarduser@cloudreach.com");
		standardAuthUser.setGroupIds(Arrays.asList(groupAdminId));
		JemoAuthentication.processRequest(standardAuthUser, requestAdapter, responseAdapter);
		//we are expecting a 400 error because the specified body is invalid
		assertEquals(new Integer(400), responseStatusCode.value);
		
		//lets try and create a user with the standard user without a username specified
		requestAdapter = new HttpServletRequestAdapter() {
			@Override
			public String getMethod() {
				return "POST";
			}

			@Override
			public String getRequestURI() {
				return "/jemo/authentication/user";
			}

			@Override
			public byte[] provideData() throws Throwable {
				JemoUser user = new JemoUser();
				return Jemo.toJSONString(user).getBytes("UTF-8");
			}
		};
		JemoAuthentication.processRequest(standardAuthUser, requestAdapter, responseAdapter);
		//we are expecting a 400 error because the specified body is invalid
		assertEquals(new Integer(400), responseStatusCode.value);
		
		//ok now we are going to create a user for test purposes jemo-test-user@cloudreach.com with the standard user but not associate it to any groups. we will expect an error 400 as an outcome.
		deleteUser("jemo-test-user@cloudreach.com");
		requestAdapter = new HttpServletRequestAdapter() {
			@Override
			public String getMethod() {
				return "POST";
			}

			@Override
			public String getRequestURI() {
				return "/jemo/authentication/user";
			}

			@Override
			public byte[] provideData() throws Throwable {
				JemoUser user = new JemoUser();
				user.setUsername("jemo-test-user@cloudreach.com");
				user.setPassword(UUID.randomUUID().toString());
				return Jemo.toJSONString(user).getBytes("UTF-8");
			}
		};
		JemoAuthentication.processRequest(standardAuthUser, requestAdapter, responseAdapter);
		//we are expecting a 400 error because the specified body is invalid
		assertEquals(new Integer(400), responseStatusCode.value);
		
		//now we will add the administrator group as a group this user should be part of and try and create it again with the standard user, we expect a 401 as an outcome.
		JemoUser user = new JemoUser();
		user.setUsername("jemo-test-user@cloudreach.com");
		user.setPassword(UUID.randomUUID().toString());
		user.setGroupIds(Arrays.asList(getAdminGroupId()));
		requestAdapter = new HttpServletRequestAdapter() {
			@Override
			public String getMethod() {
				return "POST";
			}

			@Override
			public String getRequestURI() {
				return "/jemo/authentication/user";
			}

			@Override
			public byte[] provideData() throws Throwable {
				return Jemo.toJSONString(user).getBytes("UTF-8");
			}
		};
		JemoAuthentication.processRequest(standardAuthUser, requestAdapter, responseAdapter);
		//we are expecting a 400 error because the specified body is invalid
		assertEquals(new Integer(401), responseStatusCode.value);
		//we are now going to create a group which will have this user as an administrator
		String newGroupId = createGroupWithAdminUser(standardAuthUser);
		try {
			user.setGroupIds(Arrays.asList(newGroupId));
			responseStatusCode.value = 200;
			responseAdapter.reset();
			requestAdapter = new HttpServletRequestAdapter() {
				@Override
				public String getMethod() {
					return "POST";
				}

				@Override
				public String getRequestURI() {
					return "/jemo/authentication/user";
				}

				@Override
				public byte[] provideData() throws Throwable {
					return Jemo.toJSONString(user).getBytes("UTF-8");
				}
			};
			JemoAuthentication.processRequest(standardAuthUser, requestAdapter, responseAdapter);
			//we would not expect the user to have been created
			assertEquals(new Integer(200), responseStatusCode.value);
			JemoUser newUser = JemoAuthentication.getUser(user.getUsername());
			assertNotNull(newUser);
			
			//delete the new user that was created.
			deleteUser(newUser.getUsername());
		} finally {
			//we will delete the test group that was created.
			deleteGroup(newGroupId);
		}
	}
	
	protected void deleteUser(String username) throws Throwable {
		HttpServletRequestAdapter requestAdapter = new HttpServletRequestAdapter() {
			@Override
			public String getMethod() {
				return "DELETE";
			}

			@Override
			public String getRequestURI() {
				return "/jemo/authentication/user";
			}

			@Override
			public String getParameter(String string) {
				if(string.equals(JemoAuthentication.REQ_USER)) {
					return username;
				}
				
				return null;
			}
		};
		HttpServletResponseAdapter response = new HttpServletResponseAdapter() {};
		JemoAuthentication.processRequest(getAdminUser(), requestAdapter, response);
		//assertEquals(200,response.getStatus());
	}
	
	protected JemoGroup getGroup(String groupId) throws Throwable {
		HttpServletRequestAdapter requestAdapter = new HttpServletRequestAdapter() {
			@Override
			public String getMethod() {
				return "GET";
			}

			@Override
			public String getRequestURI() {
				return "/jemo/authentication/group";
			}

			@Override
			public String getParameter(String string) {
				if(string.equals(JemoAuthentication.REQ_GROUP)) {
					return groupId;
				}
				
				return null;
			}
		};
		HttpServletResponseAdapter response = new HttpServletResponseAdapter() {};
		JemoAuthentication.processRequest(getAdminUser(), requestAdapter, response);
		if(response.getStatus() == 200) {
			return Jemo.fromJSONString(JemoGroup.class, response.getResponseBody());
		}
		
		return null;
	}
	
	protected void deleteGroup(String groupId) throws Throwable {
		HttpServletRequestAdapter requestAdapter = new HttpServletRequestAdapter() {
			@Override
			public String getMethod() {
				return "DELETE";
			}

			@Override
			public String getRequestURI() {
				return "/jemo/authentication/group";
			}

			@Override
			public String getParameter(String string) {
				if(string.equals(JemoAuthentication.REQ_GROUP)) {
					return groupId;
				}
				
				return null;
			}
		};
		HttpServletResponseAdapter response = new HttpServletResponseAdapter() {};
		JemoAuthentication.processRequest(getAdminUser(), requestAdapter, response);
		//lets make sure the group is no longer there.
		JemoGroup g = getGroup(groupId);
		assertNull(g);
	}
	
	protected String createGroupWithAdminUser(JemoUser user) throws Throwable {
		HttpServletRequestAdapter requestAdapter = new HttpServletRequestAdapter() {
			@Override
			public String getMethod() {
				return "POST";
			}

			@Override
			public String getRequestURI() {
				return "/jemo/authentication/group";
			}

			@Override
			public byte[] provideData() throws Throwable {
				JemoGroup group = new JemoGroup("Jemo Test Group", "Test Group with "+user.getUsername()+" as administrator of the group", 0, Integer.MAX_VALUE, jemoServer.getLOCATION());
				group.setAdminUsers(Arrays.asList(user.getId()));
				return Jemo.toJSONString(group).getBytes("UTF-8");
			}
		};
		HttpServletResponseAdapter response = new HttpServletResponseAdapter() {};
		JemoAuthentication.processRequest(getAdminUser(), requestAdapter, response);
		//we expect that the response body will contain the json of the group which has been created.
		JemoGroup newGroup = Jemo.fromJSONString(JemoGroup.class, response.getResponseBody());
		return newGroup.getId();
	}
	
	protected String getAdminGroupId() throws Throwable {
		HttpServletRequestAdapter request = new HttpServletRequestAdapter() {
			@Override
			public String getMethod() {
				return "GET";
			}
			
			@Override
			public String getRequestURI() {
				return "/jemo/authentication/group";
			}
		};
		HttpServletResponseAdapter response = new HttpServletResponseAdapter() {};
		//we need an admin user to get the Admin group
		JemoAuthentication.processRequest(getAdminUser(), request, response);
		JemoGroup[] groupList = (JemoGroup[]) Jemo.fromJSONString(Jemo.classOf(new JemoGroup[] {}), response.getResponseBody());
		Field groupAdminField = JemoAuthentication.class.getDeclaredField("GROUP_ADMIN");
		groupAdminField.setAccessible(true);
		for(JemoGroup group : groupList) {
			if(group.getId().equals(Util.md5((String)groupAdminField.get(JemoAuthentication.class)))) {
				return group.getId();
			}
		}
		
		return null;
	}
	
	protected JemoUser getAdminUser() throws Throwable {
		JemoUser adminUser = new JemoUser();
		adminUser.setAdmin(true);
		adminUser.setUsername("admin@cloudreach.com");
		return adminUser;
	}
}
