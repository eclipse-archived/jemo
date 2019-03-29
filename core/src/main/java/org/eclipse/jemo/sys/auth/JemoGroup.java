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

import org.eclipse.jemo.internal.model.SystemDBObject;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.jemo.internal.model.SystemDBObject;
import org.eclipse.jemo.sys.internal.Util;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 *
 * @author christopher stura
 */
public class JemoGroup implements SystemDBObject {
	private String id = null;
	private String name = null;
	private String description = null;
	private String locationPattern = null;
	private List<String> locations = null;
	private int module_id_start_range = 0;
	private int module_id_end_range = 0;
	private List<String> adminUsers = null;

	public JemoGroup() {}
	public JemoGroup(String name, String description, String locationPattern, int modStart, int modEnd) throws NoSuchAlgorithmException,UnsupportedEncodingException {
		this.name = name;
		this.description = description;
		this.id = Util.md5(this.name.toUpperCase());
		this.locationPattern = locationPattern;
		this.module_id_start_range = modStart;
		this.module_id_end_range = modEnd;
	}
	
	public JemoGroup(String name, String description, int modStart, int modEnd, String... locations) throws NoSuchAlgorithmException,UnsupportedEncodingException {
		this.name = name;
		this.description = description;
		this.id = Util.md5(this.name.toUpperCase());
		this.module_id_start_range = modStart;
		this.module_id_end_range = modEnd;
		if(locations != null) {
			this.locations = Arrays.asList(locations);
		}
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLocationPattern() {
		return locationPattern;
	}

	public void setLocationPattern(String locationPattern) {
		this.locationPattern = locationPattern;
	}

	public int getModule_id_start_range() {
		return module_id_start_range;
	}

	public void setModule_id_start_range(int module_id_start_range) {
		this.module_id_start_range = module_id_start_range;
	}

	public int getModule_id_end_range() {
		return module_id_end_range;
	}

	public void setModule_id_end_range(int module_id_end_range) {
		this.module_id_end_range = module_id_end_range;
	}

	public List<String> getLocations() {
		return locations;
	}

	public void setLocations(List<String> locations) {
		this.locations = locations;
	}

	public List<String> getAdminUsers() {
		return adminUsers;
	}

	public void setAdminUsers(List<String> adminUsers) {
		this.adminUsers = adminUsers;
	}
}
