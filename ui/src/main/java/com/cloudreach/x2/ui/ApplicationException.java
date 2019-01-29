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

import com.cloudreach.x2.ui.view.ButtonView;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author christopherstura
 */
public class ApplicationException extends Exception {
	
	private List<ApplicationError> errors = new ArrayList<>();
	
	private static String buildMessage(ButtonView operation,List<ApplicationError> errors) {
		String msg = errors.parallelStream().filter(e -> e.getKey() == null).sequential()
					.collect(Collectors.mapping(ApplicationError::getMessage, Collectors.joining("\n")));
		return msg != null && !msg.isEmpty() ? msg : operation.getTitle()+" failed with an error";
	}
	
	public ApplicationException(String message) {
		super(message);
	}
	
	public ApplicationException(ButtonView operation,ErrorList errorList) {
		super(buildMessage(operation,errorList.getErrorList()));
		this.errors.addAll(errorList.getErrorList());
	}

	public List<ApplicationError> getErrors() {
		return errors;
	}
}
