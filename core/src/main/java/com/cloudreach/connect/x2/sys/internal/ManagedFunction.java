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
package com.cloudreach.connect.x2.sys.internal;

import com.cloudreach.connect.x2.CC;
import com.cloudreach.connect.x2.internal.model.CCError;
import java.util.function.Function;
import java.util.logging.Level;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
@FunctionalInterface
public interface ManagedFunction<T,R> extends Function<T, R>{

	@Override
	public default R apply(T t) {
		try {
			return applySafe(t);
		}catch(Throwable ex) {
			CC.log(Level.SEVERE, "[%s] generated an error %s", getClass().getName(), CCError.toString(ex));
			return null;
		}
	}
	
	public R applySafe(T t) throws Throwable;
	
}
