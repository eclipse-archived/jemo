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
********************************************************************************
 */
package com.cloudreach.x2.ui.util;

/**
 * this class will be used to define a single filter on a set of data
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class DataFilter<T extends Enum> {
	private final T field;
	private final DataFilterOperator operator;
	private final Object value;
	
	protected DataFilter(T field,DataFilterOperator operator,Object value) {
		this.field = field;
		this.operator = operator;
		this.value = value;
	}

	public T getField() {
		return field;
	}

	public DataFilterOperator getOperator() {
		return operator;
	}

	public Object getValue() {
		return value;
	}
	
	public static <T extends Enum> DataFilter<T> equals(T field,Object value) {
		return new DataFilter<>(field,DataFilterOperator.EQUALS,value);
	}
	
	public static <T extends Enum> DataFilter<T> not_equals(T field,Object value) {
		return new DataFilter<>(field,DataFilterOperator.NOT_EQUALS,value);
	}
	
	public static <T extends Enum> DataFilter<T> contains(T field,Object value) {
		return new DataFilter<>(field,DataFilterOperator.CONTAINS,value);
	}
	
	public static <T extends Enum> DataFilter<T> not_contains(T field,Object value) {
		return new DataFilter<>(field,DataFilterOperator.NOT_CONTAINS,value);
	}
	
	public static <T extends Enum> DataFilter<T> greater_than(T field,Object value) {
		return new DataFilter<>(field,DataFilterOperator.GREATER_THAN,value);
	}
	
	public static <T extends Enum> DataFilter<T> less_than(T field,Object value) {
		return new DataFilter<>(field,DataFilterOperator.LESS_THAN,value);
	}
	
	public static <T extends Enum> DataFilter<T> regexp(T field,String value) {
		return new DataFilter<>(field,DataFilterOperator.REGEXP,value);
	}
}
