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

public interface JdbcEventFactory<JdbcConnectionCreation, JdbcStatementCreation, SQLExecution> {

    public JdbcConnectionCreation jdbcConnectionCreationEvent();
    public JdbcConnectionCreation jdbcConnectionCloseEvent();

    public JdbcStatementCreation jdbcStatementCreationEvent();
    public JdbcStatementCreation jdbcStatementCloseEvent();
    public JdbcStatementCreation jdbcPreparedStatementCreationEvent(String sql);
    public JdbcStatementCreation jdbcCallableStatementCreationEvent(String sql);

    public SQLExecution sqlExecutionEventForStatement(String sql);
    public SQLExecution sqlQueryExecutionEventForStatement(String sql);
    public SQLExecution sqlUpdateExecutionEventForStatement(String sql);

    public SQLExecution sqlExecutionEventForPreparedStatement(String sql);
    public SQLExecution sqlQueryExecutionEventForPreparedStatement(String sql);
    public SQLExecution sqlUpdateExecutionEventForPreparedStatement(String sql);

}
