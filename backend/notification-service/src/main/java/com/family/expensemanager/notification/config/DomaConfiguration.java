package com.family.expensemanager.notification.config;

import com.family.expensemanager.common.doma.AppDomaConfig;
import com.family.expensemanager.notification.dao.NotificationDao;
import com.family.expensemanager.notification.dao.NotificationDaoImpl;
import org.seasar.doma.jdbc.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;

@Configuration
public class DomaConfiguration {

    @Bean
    public Config domaConfig(DataSource dataSource) {
        return new AppDomaConfig(new TransactionAwareDataSourceProxy(dataSource));
    }

    @Bean
    public NotificationDao notificationDao(Config domaConfig) {
        return new NotificationDaoImpl(domaConfig);
    }
}
