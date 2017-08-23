/**
 *   Copyright (c) Shantanu Kumar. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file LICENSE at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package preflex.instrument.jdbc;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import preflex.instrument.task.CallTask1;
import preflex.instrument.task.Wrapper;

public class ConnectionWrapper<JdbcStatementCreation, SQLExecution> implements Connection {

    private final Connection conn;
    private final JdbcEventFactory<?, JdbcStatementCreation, SQLExecution> eventFactory;
    private final Wrapper<JdbcStatementCreation> stmtCreationWrapper;
    private final Wrapper<SQLExecution> sqlExecutionWrapper;

    public ConnectionWrapper(final Connection conn,
            final JdbcEventFactory<?, JdbcStatementCreation, SQLExecution> eventFactory,
            final Wrapper<JdbcStatementCreation> stmtCreationWrapper,
            final Wrapper<SQLExecution> sqlExecutionWrapper) {
        this.conn = conn;
        this.eventFactory = eventFactory;
        this.stmtCreationWrapper = stmtCreationWrapper;
        this.sqlExecutionWrapper = sqlExecutionWrapper;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return conn.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return conn.isWrapperFor(iface);
    }

    @Override
    public Statement createStatement() throws SQLException {
        final JdbcStatementCreation event = eventFactory.jdbcStatementCreationEvent();
        final CallTask1<Statement, SQLException> task = new CallTask1<Statement, SQLException>() {
            @Override
            public Statement call() throws SQLException {
                return new StatementWrapper<>(conn, conn.createStatement(), eventFactory, sqlExecutionWrapper);
            }
        };
        return stmtCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        final JdbcStatementCreation event = eventFactory.jdbcPreparedStatementCreationEvent(sql);
        final CallTask1<PreparedStatement, SQLException> task = new CallTask1<PreparedStatement, SQLException>() {
            @Override
            public PreparedStatement call() throws SQLException {
                return new PreparedStatementWrapper<>(conn, conn.prepareStatement(sql), sql, eventFactory,
                        sqlExecutionWrapper);
            }
        };
        return stmtCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public CallableStatement prepareCall(final String sql) throws SQLException {
        final JdbcStatementCreation event = eventFactory.jdbcCallableStatementCreationEvent(sql);
        final CallTask1<CallableStatement, SQLException> task = new CallTask1<CallableStatement, SQLException>() {
            @Override
            public CallableStatement call() throws SQLException {
                return new CallableStatementWrapper<>(conn, conn.prepareCall(sql), sql, eventFactory,
                        sqlExecutionWrapper);
            }
        };
        return stmtCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        return conn.nativeSQL(sql);
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        conn.setAutoCommit(autoCommit);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return conn.getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        conn.commit();
    }

    @Override
    public void rollback() throws SQLException {
        conn.rollback();
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return conn.isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return conn.getMetaData();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        conn.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return conn.isReadOnly();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        conn.setCatalog(catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return conn.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        conn.setTransactionIsolation(level);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return conn.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return conn.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        conn.clearWarnings();
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency) throws SQLException {
        final JdbcStatementCreation event = eventFactory.jdbcStatementCreationEvent();
        final CallTask1<Statement, SQLException> task = new CallTask1<Statement, SQLException>() {
            @Override
            public Statement call() throws SQLException {
                Statement stmt = conn.createStatement(resultSetType, resultSetConcurrency);
                return new StatementWrapper<>(conn, stmt, eventFactory, sqlExecutionWrapper);
            }
        };
        return stmtCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency)
            throws SQLException {
        final JdbcStatementCreation event = eventFactory.jdbcPreparedStatementCreationEvent(sql);
        final CallTask1<PreparedStatement, SQLException> task = new CallTask1<PreparedStatement, SQLException>() {
            @Override
            public PreparedStatement call() throws SQLException {
                PreparedStatement pstmt = conn.prepareStatement(sql, resultSetType, resultSetConcurrency);
                return new PreparedStatementWrapper<>(conn, pstmt, sql, eventFactory, sqlExecutionWrapper);
            }
        };
        return stmtCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency)
            throws SQLException {
        final JdbcStatementCreation event = eventFactory.jdbcCallableStatementCreationEvent(sql);
        final CallTask1<CallableStatement, SQLException> task = new CallTask1<CallableStatement, SQLException>() {
            @Override
            public CallableStatement call() throws SQLException {
                CallableStatement cstmt = conn.prepareCall(sql, resultSetType, resultSetConcurrency);
                return new CallableStatementWrapper<>(conn, cstmt, sql, eventFactory, sqlExecutionWrapper);
            }
        };
        return stmtCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return conn.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        conn.setTypeMap(map);
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        conn.setHoldability(holdability);
    }

    @Override
    public int getHoldability() throws SQLException {
        return conn.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return conn.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        return conn.setSavepoint(name);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        conn.rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        conn.releaseSavepoint(savepoint);
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability) throws SQLException {
        final JdbcStatementCreation event = eventFactory.jdbcStatementCreationEvent();
        final CallTask1<Statement, SQLException> task = new CallTask1<Statement, SQLException>() {
            @Override
            public Statement call() throws SQLException {
                Statement stmt = conn.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
                return new StatementWrapper<>(conn, stmt, eventFactory, sqlExecutionWrapper);
            }
        };
        return stmtCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability) throws SQLException {
        final JdbcStatementCreation event = eventFactory.jdbcPreparedStatementCreationEvent(sql);
        final CallTask1<PreparedStatement, SQLException> task = new CallTask1<PreparedStatement, SQLException>() {
            @Override
            public PreparedStatement call() throws SQLException {
                PreparedStatement pstmt = conn.prepareStatement(sql, resultSetType, resultSetConcurrency,
                        resultSetHoldability);
                return new PreparedStatementWrapper<>(conn, pstmt, sql, eventFactory, sqlExecutionWrapper);
            }
        };
        return stmtCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability) throws SQLException {
        final JdbcStatementCreation event = eventFactory.jdbcCallableStatementCreationEvent(sql);
        final CallTask1<CallableStatement, SQLException> task = new CallTask1<CallableStatement, SQLException>() {
            @Override
            public CallableStatement call() throws SQLException {
                CallableStatement cstmt = conn.prepareCall(sql, resultSetType, resultSetConcurrency,
                        resultSetHoldability);
                return new CallableStatementWrapper<>(conn, cstmt, sql, eventFactory, sqlExecutionWrapper);
            }
        };
        return stmtCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        final JdbcStatementCreation event = eventFactory.jdbcPreparedStatementCreationEvent(sql);
        final CallTask1<PreparedStatement, SQLException> task = new CallTask1<PreparedStatement, SQLException>() {
            @Override
            public PreparedStatement call() throws SQLException {
                PreparedStatement pstmt = conn.prepareStatement(sql, autoGeneratedKeys);
                return new PreparedStatementWrapper<>(conn, pstmt, sql, eventFactory, sqlExecutionWrapper);
            }
        };
        return stmtCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        final JdbcStatementCreation event = eventFactory.jdbcPreparedStatementCreationEvent(sql);
        final CallTask1<PreparedStatement, SQLException> task = new CallTask1<PreparedStatement, SQLException>() {
            @Override
            public PreparedStatement call() throws SQLException {
                PreparedStatement pstmt = conn.prepareStatement(sql, columnIndexes);
                return new PreparedStatementWrapper<>(conn, pstmt, sql, eventFactory, sqlExecutionWrapper);
            }
        };
        return stmtCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        final JdbcStatementCreation event = eventFactory.jdbcPreparedStatementCreationEvent(sql);
        final CallTask1<PreparedStatement, SQLException> task = new CallTask1<PreparedStatement, SQLException>() {
            @Override
            public PreparedStatement call() throws SQLException {
                PreparedStatement pstmt = conn.prepareStatement(sql, columnNames);
                return new PreparedStatementWrapper<>(conn, pstmt, sql, eventFactory, sqlExecutionWrapper);
            }
        };
        return stmtCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public Clob createClob() throws SQLException {
        return conn.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return conn.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return conn.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return conn.createSQLXML();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        return conn.isValid(timeout);
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        conn.setClientInfo(name, value);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        conn.setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        return conn.getClientInfo(name);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return conn.getClientInfo();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return conn.createArrayOf(typeName, elements);
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return conn.createStruct(typeName, attributes);
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        conn.setSchema(schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return conn.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        conn.abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        conn.setNetworkTimeout(executor, milliseconds);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return conn.getNetworkTimeout();
    }

}
