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

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.xml.ws.Holder;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFColor;

/**
 * @author christopher stura
 */
public class Datasource {
    private static final HashMap<Long, HashMap<Datasource, Connection>> connectionMap = new HashMap<>();
    private final Map<Integer, String> nativeTargetTypeMap = new HashMap<>();

    private String driverClass = null;
    private String jdbcUrl = null;
    private String username = null;
    private String password = null;
    private boolean driveSupportsIsValid = true;

    public Datasource() {
    }

    public Datasource(String driverClass, String jdbcUrl, String username, String password) {
        this.driverClass = driverClass;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public boolean isDriveSupportsIsValid() {
        return driveSupportsIsValid;
    }

    public void setDriveSupportsIsValid(boolean driveSupportsIsValid) {
        this.driveSupportsIsValid = driveSupportsIsValid;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public void setDriverClass(String driverClass) {
        this.driverClass = driverClass;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    @JsonIgnore
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.driverClass);
        hash = 97 * hash + Objects.hashCode(this.jdbcUrl);
        hash = 97 * hash + Objects.hashCode(this.username);
        return hash;
    }

    @Override
    @JsonIgnore
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Datasource other = (Datasource) obj;
        if (!Objects.equals(this.driverClass, other.driverClass)) {
            return false;
        } else if (!Objects.equals(this.jdbcUrl, other.jdbcUrl)) {
            return false;
        } else if (!Objects.equals(this.username, other.username)) {
            return false;
        }
        return true;
    }

    //we will also be able to run a transaction against this database.
    @JsonIgnore
    public void run(ManagedConsumer<Connection> consumer) throws Throwable {
        HashMap<Datasource, Connection> threadConnectionMap = connectionMap.get(Thread.currentThread().getId());
        if (threadConnectionMap == null) {
            threadConnectionMap = new HashMap<>();
            connectionMap.put(Thread.currentThread().getId(), threadConnectionMap);
        }
        //is there a connection for this datasource already open for this thread? if so we should use that with a savepoint for the transaction
        Connection dbConn = threadConnectionMap.get(this);
        boolean commit = false;
        if (dbConn != null && isDriveSupportsIsValid() && !dbConn.isValid(3)) { //if the connection exists the validate it.
            dbConn = null;
        }
        if (dbConn == null) {
            Class.forName(getDriverClass());
            dbConn = DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword());
            dbConn.setAutoCommit(false);
            threadConnectionMap.put(this, dbConn);
            commit = true;
        }
        try {
            consumer.accept(dbConn);
            if (commit && !dbConn.isClosed()) {
                dbConn.commit();
            }
        } catch (Throwable ex) {
            try {
                dbConn.rollback();
            } catch (SQLException sqlEx) {
            }//rollback until the savepoint for this particular transaction
            commit = true; //because the connection is now invalid after a failure
            throw ex;
        } finally {
            if (commit) {
                try {
                    dbConn.close();
                } catch (SQLException sqlEx) {
                }
                threadConnectionMap.remove(this);
            }
        }
    }

    @JsonIgnore
    public void query(ManagedConsumer<ResultSet> consumer, final String query, final Object... parameters) throws Throwable {
        query(rs -> true, consumer, query, parameters);
    }

    @JsonIgnore
    public void query(ManagedAcceptor<ResultSet> acceptor, ManagedConsumer<ResultSet> consumer, final String query, final Object... parameters) throws Throwable {
        query(acceptor, consumer, -1, query, parameters);
    }

    @JsonIgnore
    public void query(ManagedAcceptor<ResultSet> acceptor, ManagedConsumer<ResultSet> consumer, final int queryTimeout, final String query, final Object... parameters) throws Throwable {
        run((dbConn) -> {
            try (PreparedStatement pstm = dbConn.prepareStatement(query)) {
                if (queryTimeout != -1) {
                    pstm.setQueryTimeout(queryTimeout);
                }
                setParamList(pstm, parameters);
                try (ResultSet rs = pstm.executeQuery()) {
                    if (acceptor == null || acceptor.accept(rs)) {
                        while (rs.next()) {
                            consumer.accept(rs);
                        }
                    }
                }
            }
        });
    }

    public Stream<ResultSet> query(final Connection dbConn, final String query, final Object... parameters) {
        //the main issue with this is the fact that the connection will remain open so this cannot be an autonomous method (however in the context of a transaction it would be possible.
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<ResultSet>() {
            private PreparedStatement pstm = null;
            private ResultSet rs = null;
            private boolean hasNext = false;
            private boolean rowConsumed = false;

            @Override
            public boolean hasNext() {
                fetchNext();
                return hasNext;
            }

            @Override
            public synchronized ResultSet next() {
                fetchNext();
                rowConsumed = true;
                return rs;
            }

            protected synchronized void fetchNext() {
                try {
                    if (pstm == null) {
                        pstm = dbConn.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                        setParamList(pstm, parameters);
                        rs = pstm.executeQuery();
                        rowConsumed = true;
                    }
                    if (rowConsumed) {
                        hasNext = rs.next();
                        if (!hasNext) {
                            try {
                                rs.close();
                            } finally {
                                pstm.close();
                            }
                        }
                        rowConsumed = false;
                    }
                } catch (SQLException sqlEx) {
                    throw new RuntimeException(sqlEx);
                }
            }
        }, 0), false);
    }

    public void setParamList(PreparedStatement pstm, final Object... parameters) throws SQLException {
        if (parameters != null && parameters.length > 0) {
            for (int i = 1; i <= parameters.length; i++) {
                Object paramValue = parameters[i - 1];
                if (paramValue == null) {
                    pstm.setObject(i, null);
                } else {
                    if (paramValue instanceof Collection) {
                        paramValue = ((Collection) paramValue).toArray();
                    }
                    if (paramValue.getClass().isArray()) {
                        String type = "text";
                        if (Integer.class.isAssignableFrom(paramValue.getClass().getComponentType())) {
                            type = "int4";
                        } else if (Long.class.isAssignableFrom(paramValue.getClass().getComponentType())) {
                            type = "int8";
                        } else if (Double.class.isAssignableFrom(paramValue.getClass().getComponentType())) {
                            type = "float8";
                        } else if (Boolean.class.isAssignableFrom(paramValue.getClass().getComponentType())) {
                            type = "boolean";
                        }
                        pstm.setArray(i, pstm.getConnection().createArrayOf(type, (Object[]) paramValue));
                    } else {
                        pstm.setObject(i, paramValue);
                    }
                }
            }
        }
    }

    @JsonIgnore
    public boolean exists(final String query, Object... parameters) throws Throwable {
        final Holder<Boolean> retval = new Holder<>(false);
        query((rs) -> {
            retval.value = true;
        }, query, parameters);
        return retval.value;
    }

    @JsonIgnore
    public void save(final String query, final Object... parameters) throws Throwable {
        run((dbConn) -> {
            try (PreparedStatement pstm = dbConn.prepareStatement(query)) {
                setParamList(pstm, parameters);
                pstm.execute();
            }
        });
    }

    public Map<String, Object> query(final String query, final Object... parameters) throws Throwable {
        return query(new Holder<ResultSetMetaData>(), query, parameters);
    }

    public Map<String, Object> query(Holder<ResultSetMetaData> metaData, final String query, final Object... parameters) throws Throwable {
        final HashMap<String, Object> result = new HashMap<>();
        run((dbConn) -> {
            try (PreparedStatement pstm = dbConn.prepareStatement(query)) {
                setParamList(pstm, parameters);
                try (ResultSet rs = pstm.executeQuery()) {
                    ResultSetMetaData rsMetaData = rs.getMetaData();
                    metaData.value = rsMetaData;
                    if (rs.next()) {
                        for (int i = 1; i <= rsMetaData.getColumnCount(); i++) {
                            result.put(rsMetaData.getColumnName(i), rs.getObject(i));
                        }
                    }
                }
            }
        });

        return result;
    }

    public SqlResult queryWithResult(final String query, final Object... parameters) throws Throwable {
        Holder<ResultSetMetaData> metaData = new Holder<>();
        Map<String, Object> result = query(metaData, query, parameters);
        return new SqlResult(metaData.value, result);
    }

    public TableMetaData getMetaData(final String query, final Object... parameters) throws Throwable {
        Holder<TableMetaData> result = new Holder<>();
        //there is a bug here because if this has nothing then it never runs.
        run((dbConn) -> {
            try (PreparedStatement pstm = dbConn.prepareStatement("with q as (" + query + ") select * from q where 1 = 0")) {
                setParamList(pstm, parameters);
                try (ResultSet rs = pstm.executeQuery()) {
                    result.value = new TableMetaData(rs.getMetaData());
                }
            }
        });
        return result.value;
    }

    public boolean hasSchema(String schemaname) throws Throwable {
        return exists("select 1 from pg_namespace where lower(nspname)=lower(?)", schemaname);
    }

    public boolean hasTable(String schemaname, String tablename) throws Throwable {
        return tableExists(schemaname, tablename);
    }

    public boolean tableExists(String schemaname, String tablename) throws Throwable {
        if (driverClass.equals("org.postgresql.Driver")) {
            return exists("select 1 from pg_tables where lower(schemaname)=lower(?) and lower(tablename)=lower(?)", schemaname, tablename);
        } else if (driverClass.equals("com.microsoft.sqlserver.jdbc.SQLServerDriver") || driverClass.equals("net.sourceforge.jtds.jdbc.Driver")) {
            return exists("select 1 from sys.tables where type_desc='USER_TABLE' and lower(SCHEMA_NAME(schema_id))=lower(?) and lower(name)=lower(?)", schemaname, tablename);
        } else {
            Holder<Boolean> result = new Holder<>();
            run((dbConn) -> {
                DatabaseMetaData dbMetaData = dbConn.getMetaData();
                try (ResultSet rs = dbMetaData.getTables(null, schemaname, tablename, new String[]{"TABLE"})) {
                    result.value = rs.next();
                }
            });
            return result.value;
        }
    }

    public boolean indexExists(String schemaname, String indexname) throws Throwable {
        return exists("select ns.nspname,tbl.relname as index_name from pg_class tbl\n" +
                "inner join pg_namespace ns on ns.oid=tbl.relnamespace \n" +
                "where tbl.relkind='i' and lower(ns.nspname)=lower(?) and lower(tbl.relname)=lower(?)", schemaname, indexname);
    }

    public boolean viewExists(String schemaname, String viewname) throws Throwable {
        return exists("select ns.nspname,tbl.relname as index_name from pg_class tbl\n" +
                "inner join pg_namespace ns on ns.oid=tbl.relnamespace \n" +
                "where (tbl.relkind='v' or tbl.relkind='m') and lower(ns.nspname)=lower(?) and lower(tbl.relname)=lower(?)", schemaname, viewname);
    }

    public void execute(String sql) throws Throwable {
        run((dbConn) -> {
            try (Statement stm = dbConn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                stm.execute(sql);
            }
        });
    }

    public boolean hasColumn(String schemaname, String tablename, String columnname) throws Throwable {
        return exists("select s.nspname,tbl.relname,attr.attname from pg_attribute attr \n" +
                "inner join pg_class tbl on tbl.oid=attr.attrelid\n" +
                "inner join pg_namespace s on s.oid=tbl.relnamespace\n" +
                "where lower(s.nspname)=lower(?) and lower(tbl.relname)=lower(?) and lower(attr.attname)=lower(?)", schemaname, tablename, columnname);
    }

    public List<String> listSchemas() throws Throwable {
        ArrayList<String> result = new ArrayList<>();
        query(rs -> result.add(rs.getString("nspname")), "select nspname from pg_namespace where nspowner <> 10 order by nspname");
        return result;
    }

    public Map<String, String> listSchemasAsMap() throws Throwable {
        LinkedHashMap<String, String> linkedMap = new LinkedHashMap();
        listSchemas().stream().forEach(i -> linkedMap.put(i, i));

        return linkedMap;
    }

    public List<String> listTables(String schemaname) throws Throwable {
        ArrayList<String> result = new ArrayList<>();
        query(rs -> result.add(rs.getString("tablename")), "select tablename from pg_tables where schemaname=? and tablename not like 'systables%' order by tablename", schemaname);
        return result;
    }

    public Map<String, String> listColumnsWithDatatypes(String schemaname, String tablename) throws Throwable {
        Map<String, String> result = new LinkedHashMap<>();
        query(rs -> result.put(rs.getString("attname"), rs.getString("typname")), "select attr.attname,t.typname from pg_attribute attr\n" +
                "inner join pg_class tbl on tbl.oid=attr.attrelid\n" +
                "inner join pg_namespace nsp on nsp.oid=tbl.relnamespace\n" +
                "inner join pg_type t on t.oid=attr.atttypid\n" +
                "where lower(nsp.nspname)=lower(?) and lower(tbl.relname)=lower(?) and attr.attnum > 0", schemaname, tablename);
        return result;
    }

    public Map<String, String> listPrimaryKeyColumns(String schemaname, String tablename) throws Throwable {
        Map<String, String> result = new LinkedHashMap<>();
        query(rs -> result.put(rs.getString("attname"), rs.getString("data_type")),
                "SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS data_type\n" +
                        "FROM   pg_index i\n" +
                        "JOIN   pg_attribute a ON a.attrelid = i.indrelid\n" +
                        "                     AND a.attnum = ANY(i.indkey)\n" +
                        "WHERE  i.indrelid = ?::regclass\n" +
                        "AND    i.indisprimary", schemaname + "." + tablename);
        return result;
    }

    public Map<String, String> queryToStringMap(String query, Object... paramList) throws Throwable {
        return queryToMap(String.class, String.class, query, paramList);
    }

    public <K extends Object, V extends Object> Map<K, V> queryToMap(Class<K> keyClass, Class<V> valueClass, String query, Object... paramList) throws Throwable {
        Map<K, V> result = new LinkedHashMap<>();
        query(rs -> result.put(String.class.isAssignableFrom(keyClass) ? keyClass.cast(rs.getString(1)) : keyClass.cast(rs.getObject(1)),
                String.class.isAssignableFrom(valueClass) ? valueClass.cast(rs.getString(2)) : valueClass.cast(rs.getObject(2))), query, paramList);
        return result;
    }

    public static String jdbcTranslateToString(ResultSet rs, String columnName) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnId = 0;
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            if (metaData.getColumnName(i).equalsIgnoreCase(columnName)) {
                columnId = i;
                break;
            }
        }

        if (columnId != 0) {
            return jdbcTranslateToString(metaData, rs, columnId);
        }

        return null;
    }

    public static String jdbcTranslateToString(ResultSetMetaData metaData, ResultSet rs, int columnNumber) throws SQLException {
        return jdbcTranslateToString(rs.getObject(columnNumber), metaData.getColumnType(columnNumber));
    }

    public static String jdbcTranslateToString(Object sqlData, int sqlType) throws SQLException {
        switch (sqlType) {
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                java.sql.Timestamp ts = (java.sql.Timestamp) sqlData;
                String tsValue = null;
                if (ts != null) {
                    tsValue = new SimpleDateFormat("dd-MM-yyy HH:mm:ss").format(new java.util.Date(ts.getTime()));
                }
                return tsValue;
            case Types.BIT:
            case Types.BOOLEAN:
                if (sqlData != null) {
                    if (Boolean.class.cast(sqlData)) {
                        return "Yes";
                    } else {
                        return "No";
                    }
                } else {
                    return null;
                }
            default:
                return sqlData != null ? sqlData.toString() : null;
        }
    }

    /**
     * this is a utility method that will export any query on this data source to an XLSX file which will be streamed to
     * the output stream which is passed into the query.
     *
     * @param query the query whose results are to be exported
     * @param out the output stream
     * @param paramList the parameter list
     * @throws Throwable thrown in case of a failure
     */
    public void exportToExcel(String query, OutputStream out, Predicate<String> columnFilter, Function<String, String> columnTranslator, Object... paramList) throws Throwable {
        Holder<Integer> rowId = new Holder<>(0);
        Holder<ResultSetMetaData> rsMetadata = new Holder<>();
        SXSSFWorkbook wb = new SXSSFWorkbook(100); // keep 100 rows in memory, exceeding rows will be flushed to disk
        SXSSFSheet sh = wb.createSheet("Data Export");
        sh.trackAllColumnsForAutoSizing();
        Font boldFont = wb.createFont();
        boldFont.setBold(true);
        boldFont.setColor(IndexedColors.WHITE.getIndex());
        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFont(boldFont);
        headerStyle.setFillBackgroundColor(new XSSFColor(new byte[]{47, 64, 80}).getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Holder<Integer> numColumns = new Holder<>(0);
        query((rs) -> {
            if (rsMetadata.value == null) {
                rsMetadata.value = rs.getMetaData();
                Row row = sh.createRow(rowId.value);
                int excelColNum = 0;
                for (int cellnum = 1; cellnum <= rsMetadata.value.getColumnCount(); cellnum++) {
                    if (columnFilter == null || columnFilter.test(rsMetadata.value.getColumnName(cellnum))) {
                        Cell cell = row.createCell(excelColNum);
                        cell.setCellStyle(headerStyle);
                        cell.setCellValue((String) (columnTranslator == null ? rsMetadata.value.getColumnName(cellnum) : columnTranslator.apply(rsMetadata.value.getColumnName(cellnum))));
                        numColumns.value++;
                        excelColNum++;
                    }
                }
                rowId.value++;
            }
            Row row = sh.createRow(rowId.value);
            int excelColNum = 0;
            for (int cellnum = 1; cellnum <= rsMetadata.value.getColumnCount(); cellnum++) {
                if (columnFilter == null || columnFilter.test(rsMetadata.value.getColumnName(cellnum))) {
                    Cell cell = row.createCell(excelColNum);
                    //apply an anti-html regexp to
                    cell.setCellValue(Util.S(jdbcTranslateToString(rsMetadata.value, rs, cellnum)).replaceAll("<\\/?[^>]+(>|$)", ""));
                    excelColNum++;
                }
            }
            rowId.value++;
        }, query, paramList);
        IntStream.range(0, numColumns.value).forEach(i -> sh.autoSizeColumn(i));
        wb.write(out);
        out.close();

        // dispose of temporary files backing this workbook on disk
        wb.dispose();
    }

    public void addColumn(String schema, String table, String columnName, int JDBCType) throws Throwable {
        addColumnNative(schema, table, columnName.toLowerCase(), JDBCType);
    }

    public void addColumnNative(String schema, String table, String requestedColumnName, int JDBCType) throws Throwable {
        final String columnName = Util.truncate(requestedColumnName, 55); //column names can have a maximum of 55 characters.
        run((dbConn) -> {
            if (!hasColumn(schema, table, columnName)) {
                if (nativeTargetTypeMap.isEmpty()) {
                    DatabaseMetaData dbMetaData = dbConn.getMetaData();
                    try (ResultSet rs = dbMetaData.getTypeInfo()) {
                        while (rs.next()) {
                            switch (rs.getString("TYPE_NAME")) {
                                case "oid":
                                    break;
                                default:
                                    nativeTargetTypeMap.put(rs.getInt("DATA_TYPE"), rs.getString("TYPE_NAME"));
                            }
                        }
                    }
                    //we have a bias to text fields for sql data types
                    nativeTargetTypeMap.put(Types.LONGNVARCHAR, "text");
                    nativeTargetTypeMap.put(Types.LONGVARCHAR, "text");
                    nativeTargetTypeMap.put(Types.NCLOB, "text");
                    nativeTargetTypeMap.put(Types.NVARCHAR, "text");
                    nativeTargetTypeMap.put(Types.VARCHAR, "text");
                    nativeTargetTypeMap.put(Types.CLOB, "text");

                    //we also have a certain bias for our integer types
                    nativeTargetTypeMap.put(Types.BIGINT, "int8");
                    nativeTargetTypeMap.put(Types.INTEGER, "int4");
                    nativeTargetTypeMap.put(Types.TINYINT, "int2");

                    //we also have a bias for the decimal type because effectively postgresql has no mapping for it.
                    nativeTargetTypeMap.put(Types.DECIMAL, "float8");
                    nativeTargetTypeMap.put(Types.DOUBLE, "float8");
                    nativeTargetTypeMap.put(Types.FLOAT, "float8");
                }

                String sqlType = nativeTargetTypeMap.get(JDBCType);
                execute("alter table " + schema + "." + table + " add column \"" + columnName + "\" " + sqlType);
            }
        });
    }

    public List<String> listFieldsFromQuery(String query, Object... paramList) throws Throwable {
        ArrayList<String> result = new ArrayList<>();
        query(rs -> {
            ResultSetMetaData metaData = rs.getMetaData();
            IntStream.rangeClosed(1, metaData.getColumnCount()).forEach((ManagedIntConsumer) (i -> result.add(metaData.getColumnName(i))));
            return false;
        }, null, query, paramList);
        return result;
    }
}
