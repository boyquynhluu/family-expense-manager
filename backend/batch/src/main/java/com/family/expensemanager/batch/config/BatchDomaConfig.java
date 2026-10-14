package com.family.expensemanager.batch.config;

import javax.sql.DataSource;

import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.Naming;
import org.seasar.doma.jdbc.dialect.Dialect;
import org.seasar.doma.jdbc.dialect.MysqlDialect;

/**
 * Doma {@link Config} for the batch DAOs — same settings as common's AppDomaConfig, which the batch module does not
 * depend on: common brings spring-boot-starter-web, security and redis, none of which a run-once job wants.
 *
 * @author boyquynhluu
 */
public class BatchDomaConfig implements Config {

    private final DataSource dataSource;
    private final Dialect dialect = new MysqlDialect();
    private final Naming naming = Naming.SNAKE_LOWER_CASE;

    public BatchDomaConfig(DataSource dataSource) {
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
