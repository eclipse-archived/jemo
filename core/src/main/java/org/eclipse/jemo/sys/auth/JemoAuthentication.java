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

import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.sys.internal.SystemDB;
import org.eclipse.jemo.sys.internal.Util;

import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * this class will implement module deployment authentication. Authentication will be group based,
 * but different users will be part of specific groups. Authentication information will be stored on Amazon DynamoDB
 * and authentication information can be changed through an authorised web service on the plugin manager.
 * 
 * A user can be part of one or more groups and a group will be assigned a plugin range.
 * A group will also have a list of locations assigned to it, modules will only be deployed to locations which are in the group the user
 * is part of.
 * A user will have an assigned password which must be used for authentication to Jemo.
 * Every time an operation is executed on the plugin manager this must be written on an audit trail log service.
 * 
 * @author christopher stura
 */
public class JemoAuthentication {
	protected static final String TABLE_GROUPS = "eclipse_jemo_security_groups";
	protected static final String TABLE_USERS = "eclipse_jemo_security_users";
	
	//definintion of valid request parameters
	public static final String REQ_USER = "u";
	public static final String REQ_GROUP = "g";
	
	private static final String GROUP_ADMIN = "ADMIN";
	
	private static final String USER_ADMIN = "system.administrator@jemo.eclipse.org";
	
	public static void init(AbstractJemo jemoServer) throws NoSuchAlgorithmException, IOException {
		JemoGroup adminGroup = null;
		if(SystemDB.createTable(TABLE_GROUPS)) {
			//we need to create the default system administrator group.
			adminGroup = new JemoGroup("Jemo Administrator", "Global Administration Group", ".*", 0, Integer.MAX_VALUE);
			adminGroup.setId(Util.md5(GROUP_ADMIN));
			//save the group to the table.
			SystemDB.save(TABLE_GROUPS, adminGroup);
		}
		
		if(SystemDB.createTable(TABLE_USERS)) {
			String adminPassword = UUID.randomUUID().toString();
			if(adminGroup == null) {
				adminGroup = SystemDB.get(TABLE_GROUPS, Util.md5(GROUP_ADMIN), JemoGroup.class);
			}
			JemoUser adminUser = new JemoUser();
			adminUser.setGroups(Arrays.asList(adminGroup));
			adminUser.setUsername(USER_ADMIN);
			adminUser.setPassword(Util.md5(adminPassword));
			adminUser.setAdmin(true);
			SystemDB.save(TABLE_USERS, adminUser);
			jemoServer.LOG(Level.OFF,"System Authorisation Configured for the First Time: Admin username: %s Password: %s store this in a safe place as it will not be repeated", USER_ADMIN, adminPassword);
		}
		jemoServer.LOG(Level.INFO, "System Authentication/Authorisation Initialised successfully");
	}
	
	/**
	 * this method will return a list of all of the groups which are defined on the system.
	 * 
	 * @return a list of the groups defined on the system. 
	 */
	public static List<JemoGroup> listGroups() {
		return SystemDB.list(TABLE_GROUPS, JemoGroup.class);
	}
	
	public static JemoUser getDefaultAdminUser() throws IOException {
		return getUser(USER_ADMIN);
	}
	
	public static JemoUser getUser(String username) throws IOException {
		return SystemDB.get(TABLE_USERS, username, JemoUser.class);
	}
	
	private static void exitWithError(int errorCode,String errorMessage,HttpServletResponse response) throws IOException {
		response.setContentType("text/plain");
		response.sendError(errorCode, errorMessage);
		try(OutputStream out = response.getOutputStream()) {
			out.write(errorMessage.getBytes("UTF-8"));
		}
	}
	
	public static void processRequest(JemoUser authUser, HttpServletRequest request, HttpServletResponse response) throws IOException,NoSuchAlgorithmException {
		if(authUser == null) {
			exitWithError(401,"Only authenticated users can access this service",response); return;
		}
		String userItem = request.getParameter(REQ_USER);
		String groupItem = request.getParameter(REQ_GROUP);
		if("/jemo/authentication/user".equals(request.getRequestURI())) {
			if("POST".equals(request.getMethod())) {
				//we are going to be upserting a user
				JemoUser user = Util.fromJSONString(JemoUser.class, Util.toString(request.getInputStream()));
				
				if(user == null) {
					exitWithError(400, "A user specification is required in the body of the request", response); return;
				}
				//a username is required
				if(user.getUsername() == null) {
					exitWithError(400, "The username is a required field", response); return;
				}
				//the password set here should actually be saved differently if it has been set.
				if(user.getPassword() != null) {
					user.setPassword(Util.md5(user.getPassword())); //has the password that was passed as we should not be able to see it directly in the database.
				}
				//now we need to check if a user with this username already exists.
				JemoUser existingUser = getUser(user.getUsername());
				if(existingUser != null) {
					//we need to merge the existing user data onto the posted user data
					user.merge(existingUser);
				}
				
				//we need to check some security:
				//4. we cannot remove a user from all groups.
				if(user.getGroups() == null || user.getGroups().isEmpty()) {
					exitWithError(400, "A user must belong to at least 1 group", response); return;
				}
				//1. we can only create new users we are administrators of the groups the need to be created in.
				if(!authUser.canCreateUser(user)) {
					exitWithError(401, "The user cannot be created in the groups specified because the authenticated user does not have sufficient privileges.", response); return;
				}
				//2. we can only update ourselves or users in groups we administer.
				if(!authUser.canUpdateUser(user)) {
					exitWithError(401, "The user cannot be updated in the groups specified because the authenticated user does not have sufficient privileges.", response); return;
				}
				//3. we can only remove users from groups we are administrators of.
				if(existingUser != null) {
					existingUser.getGroups().removeIf(g -> user.getGroupIds().contains(g.getId()));
					if(!existingUser.getGroupIds().isEmpty() && !authUser.canUpdateUser(existingUser)) {
						exitWithError(401, "The user cannot be removed from groups which you do not have the permission to administer", response);
					}
				}
				SystemDB.save(TABLE_USERS, user); //save the user to the database.
			} else if("DELETE".equals(request.getMethod())) {
				//we are going to be deleting a user
				JemoUser delUser = getUser(userItem);
				if(delUser == null) {
					exitWithError(404, String.format("%s not found",userItem), response);
				} else {
					if(!authUser.canUpdateUser(delUser)) {
						exitWithError(401, String.format("you do not have permission to delete the user %s",userItem), response);
					} else {
						//we need a delete function in the system db class.
						SystemDB.delete(TABLE_USERS, delUser);
						writeAsJSON(response, delUser);
					}
				}
			} else if("GET".equals(request.getMethod())) {
				//we should return a list of the users on the system.
				List<JemoUser> userList = SystemDB.list(TABLE_USERS, JemoUser.class);
				userList.stream().forEach(user -> user.extractGroups());
				writeAsJSON(response,userList.stream()
					.filter(user -> (!user.isAdmin() || authUser.isAdmin()) 
						&& (userItem == null || user.getUsername().contains(userItem)) 
						&& user.getGroups().stream().anyMatch(g -> (authUser.isAdmin() || authUser.getGroupIds().contains(g.getId())) && (groupItem == null || g.getName().contains(groupItem)))));
			}
		} else if("/jemo/authentication/group".equals(request.getRequestURI())) {
			if("POST".equals(request.getMethod())) {
				//we need to check if this group already exists.
				JemoGroup group = Util.fromJSONString(JemoGroup.class, Util.toString(request.getInputStream()));
				if(group != null) {
					JemoGroup existingGroup = SystemDB.get(TABLE_GROUPS, group.getId(), JemoGroup.class);
					if(existingGroup != null && authUser.canUpdateGroup(existingGroup)) {
						//we are allowed to update the group
						SystemDB.save(TABLE_GROUPS, group);
						writeAsJSON(response, group);
					} else if(existingGroup == null && authUser.canCreateGroup(group)) {
						//we are allowed to create the group
						SystemDB.save(TABLE_GROUPS, group);
						writeAsJSON(response, group);
					} else {
						exitWithError(401, String.format("You do not have access to %s the group %s -> %s",existingGroup != null ? "modify" : "create",existingGroup != null ? existingGroup.getId() : "NEW GROUP",existingGroup != null ? existingGroup.getName() : group.getName()), response);
					}
				} else {
					exitWithError(400, "The group payload is invalid", response);
				}
			} else if("GET".equals(request.getMethod())) {
				List<JemoGroup> groupList = new ArrayList<>();
				if(groupItem != null) {
					JemoGroup group = SystemDB.get(TABLE_GROUPS, groupItem, JemoGroup.class);
					if(group != null) {
						groupList.add(group);
					} else {
						exitWithError(404,String.format("The group %s could not be found", groupItem),response);
						return;
					}
				} else {
					//lets add a list of all the groups here
					groupList.addAll(listGroups());
				}
				if(userItem != null) {
					//remove all the elements from the group list which do not have this user as an administrator or who this user is not a part of.
					JemoUser user = SystemDB.get(TABLE_USERS, userItem, JemoUser.class);
					if(user == null) {
						exitWithError(404,String.format("The user %s could not be found and therefore cannot act as a group filter", userItem),response);
						return;
					} else {
						groupList.removeIf(g -> !((g.getAdminUsers() != null && g.getAdminUsers().contains(user.getId())) || user.getGroupIds().contains(g.getId())));
					}
				}
				//now if the authenticated user is not an admin remove any groups from this list which the current user is an administrator of.
				if(!authUser.isAdmin()) {
					groupList.removeIf(g -> g.getAdminUsers() != null && !g.getAdminUsers().contains(authUser.getId()));
				}
				writeAsJSON(response, groupList);
			} else if("DELETE".equals(request.getMethod())) {
				if(groupItem != null) {
					JemoGroup group = SystemDB.get(TABLE_GROUPS, groupItem, JemoGroup.class);
					if(group == null) {
						exitWithError(404,String.format("The group %s could not be found",groupItem),response);
					} else {
						SystemDB.delete(TABLE_GROUPS, group);
						writeAsJSON(response, group);
					}
				} else {
					exitWithError(400,"A group to delete must be specified",response);
				}
			}
		}
	}
	
	public static void writeAsJSON(HttpServletResponse response,Object obj) throws IOException {
		response.setContentType("application/json");
		try(OutputStream out = response.getOutputStream()) {
			out.write(Util.toJSONString(obj).getBytes("UTF-8"));
			out.flush();
		}
	}
}
