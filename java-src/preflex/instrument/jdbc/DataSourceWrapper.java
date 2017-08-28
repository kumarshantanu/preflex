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

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import preflex.instrument.task.CallTask1;
import preflex.instrument.task.Wrapper;

public class DataSourceWrapper<JdbcConnectionCreation, JdbcStatementCreation, SQLExecution> implements DataSource {

    private final DataSource ds;
    private final JdbcEventFactory<JdbcConnectionCreation, JdbcStatementCreation, SQLExecution> eventFactory;
    private final Wrapper<JdbcConnectionCreation> connCreationWrapper;
    private final Wrapper<JdbcStatementCreation> stmtCreationListener;
    private final Wrapper<SQLExecution> sqlExecutionListener;

    public DataSourceWrapper(DataSource ds,
            final JdbcEventFactory<JdbcConnectionCreation, JdbcStatementCreation, SQLExecution> eventFactory,
            final Wrapper<JdbcConnectionCreation> connCreationWrapper,
            final Wrapper<JdbcStatementCreation> stmtCreationWrapper,
            final Wrapper<SQLExecution> sqlExecutionWrapper) {
        this.ds = ds;
        this.eventFactory = eventFactory;
        this.connCreationWrapper = connCreationWrapper;
        this.stmtCreationListener = stmtCreationWrapper;
        this.sqlExecutionListener = sqlExecutionWrapper;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return ds.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        ds.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        ds.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return ds.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return ds.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return ds.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return ds.isWrapperFor(iface);
    }

    @Override
    public Connection getConnection() throws SQLException {
        final JdbcConnectionCreation event = eventFactory.jdbcConnectionCreationEvent();
        final CallTask1<Connection, SQLException> task = new CallTask1<Connection, SQLException>() {
            @Override
            public Connection call() throws SQLException {
                return new ConnectionWrapper<>(ds.getConnection(), eventFactory, stmtCreationListener,
                        sqlExecutionListener);
            }
        };
        return connCreationWrapper.call(event, task, SQLException.class);
    }

    @Override
    public Connection getConnection(final String username, final String password) throws SQLException {
        final JdbcConnectionCreation event = eventFactory.jdbcConnectionCreationEvent();
        final CallTask1<Connection, SQLException> task = new CallTask1<Connection, SQLException>() {
            @Override
            public Connection call() throws SQLException {
                return new ConnectionWrapper<>(ds.getConnection(username, password), eventFactory,
                        stmtCreationListener, sqlExecutionListener);
            }
        };
        return connCreationWrapper.call(event, task, SQLException.class);
    }

}
