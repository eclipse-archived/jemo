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
package com.cloudreach.x2.ui.view.form;

import com.cloudreach.x2.ui.util.Util;
import com.cloudreach.x2.ui.view.FormComponentView;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author Christopher Stura <christopher.stura@cloudreach.com>
 */
public class ImageView extends FormComponentView<ImageView> {
	private String src = null;
	private boolean allowEdit = false;
	
	public ImageView() { super(); }
	
	public ImageView(String title,String imageUrl) {
		this();
		
		setTitle(title);
		this.src = imageUrl;
	}
	
	public ImageView(String title,String contentType,byte[] imageBytes) {
		this(title,"data:"+contentType+";base64,"+Util.base64(imageBytes));
	}

	public String getSrc() {
		return src;
	}

	public ImageView setSrc(String src) {
		this.src = src;
		return this;
	}

	@JsonProperty(value = "edit")
	public boolean isAllowEdit() {
		return allowEdit;
	}

	@JsonProperty(value = "edit")
	public ImageView setAllowEdit(boolean allowEdit) {
		this.allowEdit = allowEdit;
		return this;
	}
}
