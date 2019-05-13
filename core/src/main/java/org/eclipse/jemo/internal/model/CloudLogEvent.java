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
import java.io.Serializable;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class CloudLogEvent implements Serializable {
	private long timestamp = System.currentTimeMillis();
	private String message = null;
	private int moduleId = -1;
	private String moduleName = null;
	private double moduleVersion = 1.0;
	private String level;
	
	public CloudLogEvent(String message) {
		this.message = message;
	}
	
	public CloudLogEvent() {}

	public long getTimestamp() {
		return timestamp;
	}

	public String getMessage() {
		return message;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public int getModuleId() {
		return moduleId;
	}

	public void setModuleId(int moduleId) {
		this.moduleId = moduleId;
	}

	public String getModuleName() {
		return moduleName == null ? "ECLIPSE-JEMO" : moduleName;
	}

	public void setModuleName(String moduleName) {
		this.moduleName = moduleName;
	}

	public double getModuleVersion() {
		return moduleVersion;
	}

	public void setModuleVersion(double moduleVersion) {
		this.moduleVersion = moduleVersion;
	}

	public String getLevel() {
		return level;
	}

	public void setLevel(String level) {
		this.level = level;
	}

	@JsonIgnore
	public CloudLogEvent withModuleId(int moduleId) {
		setModuleId(moduleId);
		return this;
	}
	
	@JsonIgnore
	public CloudLogEvent withModuleName(String moduleName) {
		setModuleName(moduleName);
		return this;
	}
	
	@JsonIgnore
	public CloudLogEvent withModuleVersion(double moduleVersion) {
		setModuleVersion(moduleVersion);
		return this;
	}
}
