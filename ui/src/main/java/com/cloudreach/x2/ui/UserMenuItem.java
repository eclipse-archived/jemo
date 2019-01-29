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
package com.cloudreach.x2.ui;

import com.cloudreach.x2.ui.view.View;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author Christopher Stura <christopher.stura@cloudreach.com>
 */
public class UserMenuItem extends View {
	private Class<? extends Button> action = null;
	private String title = null;
	private String target = Application.MAIN_APPLICATION_AREA;
	private boolean divider = false;

	@JsonIgnore
	public Class<? extends Button> getAction() {
		return action;
	}

	@JsonIgnore
	public UserMenuItem setAction(Class<? extends Button> action) {
		this.action = action;
		return this;
	}
	
	@JsonProperty(value = "action")
	public String getActionStr() {
		return action != null ? action.getName() : null;
	}

	public String getTitle() {
		return title;
	}

	public UserMenuItem setTitle(String title) {
		this.title = title;
		return this;
	}

	public String getTarget() {
		return target;
	}

	public UserMenuItem setTarget(String target) {
		this.target = target;
		return this;
	}

	public boolean isDivider() {
		return divider;
	}

	public UserMenuItem setDivider(boolean divider) {
		this.divider = divider;
		return this;
	}
	
	
}
