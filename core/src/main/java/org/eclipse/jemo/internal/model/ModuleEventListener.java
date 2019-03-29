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
package org.eclipse.jemo.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 * @author christopher stura
 */
public class ModuleEventListener {
	private int pluginId = 0;
	private String moduleClass = null;
	private String location = null;

	public ModuleEventListener(int pluginId,String moduleClass,String location) {
		this.pluginId = pluginId;
		this.moduleClass = moduleClass;
		this.location = location;
	}
	
	public int getPluginId() {
		return pluginId;
	}

	public void setPluginId(int pluginId) {
		this.pluginId = pluginId;
	}

	public String getModuleClass() {
		return moduleClass;
	}

	public void setModuleClass(String moduleClass) {
		this.moduleClass = moduleClass;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	@JsonIgnore
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof ModuleEventListener) {
			return toString().equals(obj.toString());
		}
		
		return false;
	}

	@JsonIgnore
	@Override
	public String toString() {
		return String.format("plugin[%d] module[%s] location[%s]",getPluginId(),getModuleClass(),getLocation());
	}

	@JsonIgnore
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
	
}
