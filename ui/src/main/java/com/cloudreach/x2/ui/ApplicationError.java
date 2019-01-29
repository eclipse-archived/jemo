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

/**
 *
 * @author christopher stura
 */
public class ApplicationError {
	private String key = null;
	private String message = null;
	private String detail = null;
	
	public ApplicationError() {}
	public ApplicationError(String key,String message) {
		this.key = key;
		this.message = message;
	}

	public String getKey() {
		return key;
	}

	public ApplicationError setKey(String key) {
		this.key = key;
		return this;
	}

	public String getMessage() {
		return message;
	}

	public ApplicationError setMessage(String message) {
		this.message = message;
		return this;
	}

	public String getDetail() {
		return detail;
	}

	public ApplicationError setDetail(String detail) {
		this.detail = detail;
		return this;
	}
}
