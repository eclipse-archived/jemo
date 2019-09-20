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
import java.sql.Types;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * this class will make it easy to process scalar sql results
 * queries which return a single line.
 *
 * @author christopher stura
 */
public class SqlResult {
    private Map<String, Object> data = new HashMap<>();
    private ResultSetMetaData metaData = null;

    public SqlResult(ResultSetMetaData metaData, Map<String, Object> data) {
        this.data.putAll(data);
        this.metaData = metaData;
    }

    public int getInt(String columnName) {
        Object dataValue = data.get(columnName);
        if (dataValue != null) {
            if (dataValue instanceof Integer)
                return (Integer) dataValue;
            else if (dataValue instanceof Long)
                return Long.class.cast(dataValue).intValue();
            else if (dataValue instanceof Double)
                return Double.class.cast(dataValue).intValue();
            else if (dataValue != null)
                return new Double(dataValue.toString()).intValue();
        }

        return 0;
    }

    public long getLong(String columnName) {
        Object objVal = data.get(columnName);
        if (objVal != null) {
            if (objVal instanceof Integer)
                return Integer.class.cast(objVal).longValue();
            else if (objVal instanceof Long)
                return Long.class.cast(objVal);
            else if (objVal instanceof Double)
                return Double.class.cast(objVal).longValue();
            else if (objVal != null)
                return new Double(objVal.toString()).longValue();
        }

        return 0;
    }

    public String getString(String columnName) {
        return (String) data.get(columnName);
    }

    public String asString(String columnName) throws SQLException {
        int sqlType = Types.VARCHAR;
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            if (metaData.getColumnName(i).equals(columnName)) {
                sqlType = metaData.getColumnType(i);
                break;
            }
        }
        return Datasource.jdbcTranslateToString(data.get(columnName), sqlType);
    }

    public Boolean getBoolean(String columnName) {
        Object dataValue = data.get(columnName);
        if (dataValue instanceof Boolean) {
            return (Boolean) dataValue;
        } else if (dataValue != null) {
            switch (dataValue.toString().toLowerCase()) {
                case "yes":
                case "true":
                case "t":
                case "y":
                    return true;
                case "no":
                case "false":
                case "f":
                case "n":
                    return false;
            }
        }

        return null;
    }

    /**
     * this method will convert the value of a field into a java.util.Date field,
     * this will work for any type of column that contains a data value in it.
     *
     * @param columnName
     * @return the date
     */
    public java.util.Date getDate(String columnName) {
        Object objVal = data.get(columnName);
        if (objVal != null) {
            if (objVal instanceof java.sql.Date) {
                return new java.util.Date(((java.sql.Date) objVal).getTime());
            } else if (objVal instanceof java.sql.Time) {
                return new java.util.Date(((java.sql.Time) objVal).getTime());
            } else if (objVal instanceof java.sql.Timestamp) {
                return new java.util.Date(((java.sql.Timestamp) objVal).getTime());
            } else {
                try {
                    return Util.DATE(objVal.toString());
                } catch (ParseException parseEx) {
                    return null; //null on unparseable date.
                }
            }
        }

        return null;
    }
}
