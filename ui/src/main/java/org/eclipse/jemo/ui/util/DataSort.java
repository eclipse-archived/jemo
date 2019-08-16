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
package org.eclipse.jemo.ui.util;

/**
 * this class will help to define the way data should be sorted.
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class DataSort<T extends Enum> {
    private final T field;
    private final DataSortOrder order;
    private final boolean nullfirst;

    protected DataSort(T field, DataSortOrder order, boolean nullfirst) {
        this.field = field;
        this.order = order;
        this.nullfirst = nullfirst;
    }

    public T getField() {
        return field;
    }

    public DataSortOrder getOrder() {
        return order;
    }

    public boolean isNullfirst() {
        return nullfirst;
    }

    public static <T extends Enum> DataSort<T> asc(T field, boolean nullfirst) {
        return new DataSort(field, DataSortOrder.ASC, nullfirst);
    }

    public static <T extends Enum> DataSort<T> desc(T field, boolean nullfirst) {
        return new DataSort(field, DataSortOrder.DESC, nullfirst);
    }
}
