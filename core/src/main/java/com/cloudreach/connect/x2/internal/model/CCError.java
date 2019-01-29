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
package com.cloudreach.connect.x2.internal.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

/**
 *
 * @author christopherstura
 */
public class CCError {
	private String message = null;
	private String stackTrace = null;

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getStackTrace() {
		return stackTrace;
	}

	public void setStackTrace(String stackTrace) {
		this.stackTrace = stackTrace;
	}
	
	@JsonIgnore
	public static CCError newInstance(Throwable ex) {
		CCError retval = new CCError();
		retval.setMessage(ex.getMessage());
		retval.setStackTrace(toString(ex));
		
		return retval;
	}

	@Override
	public String toString() {
		return "CCError{" + "stackTrace=" + stackTrace + '}';
	}
	
	public static String toString(Throwable ex) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ex.printStackTrace(new PrintWriter(out, true));
		try {
			return out.toString("UTF-8");
		}catch(UnsupportedEncodingException ex2) {}
		
		return null;
	}
}
