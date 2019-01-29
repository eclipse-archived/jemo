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
package com.cloudreach.connect.x2.api;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class KeyValue<V extends Object> {
	private final String key;
	private V value = null;
	
	public KeyValue(String key) {
		this(key,null);
	}
	
	public KeyValue(String key,V value) {
		this.key = key;
		this.value = value;
	}

	public String getKey() {
		return key;
	}

	public V getValue() {
		return value;
	}

	public void setValue(V value) {
		this.value = value;
	}
	
	public static <V extends Object> KeyValue<V> of(String key,V value) {
		return new KeyValue<>(key,value);
	}
}
