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
package org.eclipse.jemo.ui.util;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TableMetaData {

    private Map<String, ColumnMetaData> columns = new LinkedHashMap<>();

    public TableMetaData(ResultSetMetaData metaData) throws SQLException {
        IntStream.rangeClosed(1, metaData.getColumnCount()).forEach(i -> {
            try {
                if (!columns.containsKey(metaData.getColumnName(i))) { //add only if it is not already there.
                    columns.put(metaData.getColumnName(i), new ColumnMetaData(metaData, i));
                }
            } catch (SQLException sqlEx) {
                throw new RuntimeException(sqlEx);
            }
        });
    }

    public int getColumnCount() {
        return columns.size();
    }

    private ColumnMetaData getColumnMetaData(int columnPos) {
        Iterator<Map.Entry<String, ColumnMetaData>> itr = columns.entrySet().iterator();
        Map.Entry<String, ColumnMetaData> entry = null;
        for (int i = 0; i < columnPos; i++) {
            entry = itr.next();
        }

        return entry.getValue();
    }

    public String getColumnName(int columnPos) {
        return getColumnMetaData(columnPos).getName();
    }

    public int getColumnType(int columnPos) {
        return getColumnMetaData(columnPos).getType();
    }

    public boolean hasColumn(String columnName) {
        return columns.containsKey(columnName);
    }

    public ColumnMetaData getColumnMetaData(String columnName) {
        return columns.get(columnName);
    }
}
