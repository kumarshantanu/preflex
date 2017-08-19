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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;

import preflex.instrument.EventHandlerFactory;
import preflex.instrument.task.CallTask1;
import preflex.instrument.task.InstrumentingWrapper;
import preflex.instrument.task.Wrapper;

public class StatementWrapper<SQLExecution> implements Statement {

    private final Connection conn;
    private final Statement stmt;
    private final JdbcEventFactory<?, ?, SQLExecution> eventFactory;
    private final Wrapper<SQLExecution> sqlExecutionWrapper;

    public StatementWrapper(final Connection conn, final Statement stmt,
            final JdbcEventFactory<?, ?, SQLExecution> eventFactory,
            final EventHandlerFactory<SQLExecution> sqlExecutionListener) {
        this.conn = conn;
        this.stmt = stmt;
        this.eventFactory = eventFactory;
        this.sqlExecutionWrapper = new InstrumentingWrapper<>(sqlExecutionListener);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return stmt.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return stmt.isWrapperFor(iface);
    }

    @Override
    public ResultSet executeQuery(final String sql) throws SQLException {
        if (sql == null) {
            throw new NullPointerException("Expected valid SQL, but found NULL");
        }
        final SQLExecution event = eventFactory.sqlQueryExecutionEventForStatement(sql);
        final CallTask1<ResultSet, SQLException> task = new CallTask1<ResultSet, SQLException>() {
            @Override
            public ResultSet call() throws SQLException {
                return stmt.executeQuery(sql);
            }
        };
        return sqlExecutionWrapper.call(event, task, SQLException.class);
    }

    @Override
    public int executeUpdate(final String sql) throws SQLException {
        if (sql == null) {
            throw new NullPointerException("Expected valid SQL, but found NULL");
        }
        final SQLExecution event = eventFactory.sqlUpdateExecutionEventForStatement(sql);
        final CallTask1<Integer, SQLException> task = new CallTask1<Integer, SQLException>() {
            @Override
            public Integer call() throws SQLException {
                return stmt.executeUpdate(sql);
            }
        };
        return sqlExecutionWrapper.call(event, task, SQLException.class);
    }

    @Override
    public void close() throws SQLException {
        stmt.close();
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return stmt.getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        stmt.setMaxFieldSize(max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return stmt.getMaxRows();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        stmt.setMaxRows(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        stmt.setEscapeProcessing(enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return stmt.getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        stmt.setQueryTimeout(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        stmt.cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return stmt.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        stmt.clearWarnings();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        stmt.setCursorName(name);
    }

    @Override
    public boolean execute(final String sql) throws SQLException {
        if (sql == null) {
            throw new NullPointerException("Expected valid SQL, but found NULL");
        }
        final SQLExecution event = eventFactory.sqlExecutionEventForStatement(sql);
        final CallTask1<Boolean, SQLException> task = new CallTask1<Boolean, SQLException>() {
            @Override
            public Boolean call() throws SQLException {
                return stmt.execute(sql);
            }
        };
        return sqlExecutionWrapper.call(event, task, SQLException.class);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return stmt.getResultSet();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return stmt.getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return stmt.getMoreResults();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        stmt.setFetchDirection(direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return stmt.getFetchDirection();
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        stmt.setFetchSize(rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return stmt.getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return stmt.getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return stmt.getResultSetType();
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        stmt.addBatch(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        stmt.clearBatch();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        return stmt.executeBatch();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return conn;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return stmt.getMoreResults();
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return stmt.getGeneratedKeys();
    }

    @Override
    public int executeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        final SQLExecution event = eventFactory.sqlUpdateExecutionEventForStatement(sql);
        final CallTask1<Integer, SQLException> task = new CallTask1<Integer, SQLException>() {
            @Override
            public Integer call() throws SQLException {
                return stmt.executeUpdate(sql, autoGeneratedKeys);
            }
        };
        return sqlExecutionWrapper.call(event, task, SQLException.class);
    }

    @Override
    public int executeUpdate(final String sql, final int[] columnIndexes) throws SQLException {
        final SQLExecution event = eventFactory.sqlUpdateExecutionEventForStatement(sql);
        final CallTask1<Integer, SQLException> task = new CallTask1<Integer, SQLException>() {
            @Override
            public Integer call() throws SQLException {
                return stmt.executeUpdate(sql, columnIndexes);
            }
        };
        return sqlExecutionWrapper.call(event, task, SQLException.class);
    }

    @Override
    public int executeUpdate(final String sql, final String[] columnNames) throws SQLException {
        final SQLExecution event = eventFactory.sqlUpdateExecutionEventForStatement(sql);
        final CallTask1<Integer, SQLException> task = new CallTask1<Integer, SQLException>() {
            @Override
            public Integer call() throws SQLException {
                return stmt.executeUpdate(sql, columnNames);
            }
        };
        return sqlExecutionWrapper.call(event, task, SQLException.class);
    }

    @Override
    public boolean execute(final String sql, final int autoGeneratedKeys) throws SQLException {
        final SQLExecution event = eventFactory.sqlExecutionEventForStatement(sql);
        final CallTask1<Boolean, SQLException> task = new CallTask1<Boolean, SQLException>() {
            @Override
            public Boolean call() throws SQLException {
                return stmt.execute(sql, autoGeneratedKeys);
            }
        };
        return sqlExecutionWrapper.call(event, task, SQLException.class);
    }

    @Override
    public boolean execute(final String sql, final int[] columnIndexes) throws SQLException {
        final SQLExecution event = eventFactory.sqlExecutionEventForStatement(sql);
        final CallTask1<Boolean, SQLException> task = new CallTask1<Boolean, SQLException>() {
            @Override
            public Boolean call() throws SQLException {
                return stmt.execute(sql, columnIndexes);
            }
        };
        return sqlExecutionWrapper.call(event, task, SQLException.class);
    }

    @Override
    public boolean execute(final String sql, final String[] columnNames) throws SQLException {
        final SQLExecution event = eventFactory.sqlExecutionEventForStatement(sql);
        final CallTask1<Boolean, SQLException> task = new CallTask1<Boolean, SQLException>() {
            @Override
            public Boolean call() throws SQLException {
                return stmt.execute(sql, columnNames);
            }
        };
        return sqlExecutionWrapper.call(event, task, SQLException.class);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return stmt.getResultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return stmt.isClosed();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        stmt.setPoolable(poolable);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return stmt.isPoolable();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        stmt.closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return stmt.isCloseOnCompletion();
    }

}
