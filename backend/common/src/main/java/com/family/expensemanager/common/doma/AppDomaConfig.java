package com.family.expensemanager.common.doma;

import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.Naming;
import org.seasar.doma.jdbc.dialect.Dialect;
import org.seasar.doma.jdbc.dialect.MysqlDialect;

import javax.sql.DataSource;

/**
 * Doma {@link Config} shared by every service. The DataSource passed in must be a
 * {@code TransactionAwareDataSourceProxy} wrapping the Spring-managed DataSource, so DAO
 * calls made inside a {@code @Transactional} method reuse the same JDBC connection/transaction
 * instead of opening a new one — see each service's {@code DomaConfiguration}.
 */
public class AppDomaConfig implements Config {

    private final DataSource dataSource;
    private final Dialect dialect = new MysqlDialect();
    private final Naming naming = Naming.SNAKE_LOWER_CASE;

    public AppDomaConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public Dialect getDialect() {
        return dialect;
    }

    @Override
    public Naming getNaming() {
        return naming;
    }
}
