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
package com.cloudreach.x2.ui.view;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * this view represents a dialog overlay it is actually just a very simple floating container
 *
 * @author christopher stura
 */
public class DialogView extends AreaView {
	
	private String title = null;
	private String width = null;
	
	public DialogView(String title) {
		this.title = title;
	}

	@JsonProperty(value = "width")
	public String getWidth() {
		return width;
	}

	@JsonProperty(value = "width")
	public DialogView setWidth(String width) {
		this.width = width;
		
		return this;
	}
	
	@JsonProperty(value = "title")
	public String getTitle() {
		return title;
	}

	@JsonProperty(value = "title")
	public DialogView setTitle(String title) {
		this.title = title;
		
		return this;
	}
}
