package com.family.expensemanager.batch.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Two DataSources (README "Batch" → "Đọc/ghi dữ liệu nghiệp vụ"). Declaring any DataSource bean switches Spring
 * Boot's own off, so both are declared here:
 * <ul>
 *   <li>{@code dataSource} — fem_batch ({@code spring.datasource.*}), {@code @Primary}: Spring Batch's
 *       JobRepository, the chunk/tasklet transaction manager and Flyway all take the primary one, and they must
 *       all point at fem_batch;</li>
 *   <li>{@code expenseDataSource} — fem_expense ({@code fem.expense.datasource.*}) with a SELECT-only user, so a
 *       job bug cannot write business data. Always inject it with {@code @Qualifier("expenseDataSource")} —
 *       without it Spring hands you the primary one.</li>
 * </ul>
 *
 * @author boyquynhluu
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties batchDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties batchDataSourceProperties) {
        return batchDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    @ConfigurationProperties("fem.expense.datasource")
    public DataSourceProperties expenseDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource expenseDataSource(
            @Qualifier("expenseDataSourceProperties") DataSourceProperties expenseDataSourceProperties) {
        return expenseDataSourceProperties.initializeDataSourceBuilder().build();
    }
}
