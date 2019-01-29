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

import com.cloudreach.x2.ui.view.FormComponentView;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author christopher stura
 */
public class CodeView extends FormComponentView<CodeView> {
	
	public static enum CodeLanguage {
		TRANSACT_SQL("text/x-mssql"),JAVASCRIPT("text/javascript");
		
		String mode = null;
		CodeLanguage(String mode) {
			this.mode = mode;
		}

		@Override
		public String toString() {
			return mode;
		}
	}
	
	private CodeLanguage language = null;
	private String value = null;
	
	public CodeView() {}
	
	public CodeView(String name,CodeLanguage language) {
		super(name);
		
		this.language = language;
	}
	
	public String getMode() {
		return this.language.toString();
	}

	@JsonProperty(value = "value")
	public String getValue() {
		return value;
	}

	@JsonIgnore
	public CodeView setValue(String value) {
		this.value = value;
		
		return this;
	}
	
	
}
