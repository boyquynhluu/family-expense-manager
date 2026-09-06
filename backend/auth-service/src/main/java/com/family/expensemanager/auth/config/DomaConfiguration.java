package com.family.expensemanager.auth.config;

import com.family.expensemanager.auth.dao.FamilyDao;
import com.family.expensemanager.auth.dao.FamilyDaoImpl;
import com.family.expensemanager.auth.dao.RefreshTokenDao;
import com.family.expensemanager.auth.dao.RefreshTokenDaoImpl;
import com.family.expensemanager.auth.dao.UserDao;
import com.family.expensemanager.auth.dao.UserDaoImpl;
import com.family.expensemanager.common.doma.AppDomaConfig;
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
    public FamilyDao familyDao(Config domaConfig) {
        return new FamilyDaoImpl(domaConfig);
    }

    @Bean
    public UserDao userDao(Config domaConfig) {
        return new UserDaoImpl(domaConfig);
    }

    @Bean
    public RefreshTokenDao refreshTokenDao(Config domaConfig) {
        return new RefreshTokenDaoImpl(domaConfig);
    }
}
