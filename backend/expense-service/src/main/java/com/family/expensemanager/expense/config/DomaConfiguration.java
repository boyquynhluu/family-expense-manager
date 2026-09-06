package com.family.expensemanager.expense.config;

import com.family.expensemanager.common.doma.AppDomaConfig;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.BudgetDaoImpl;
import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.dao.CategoryDaoImpl;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.TransactionDaoImpl;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.dao.WalletDaoImpl;
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
    public WalletDao walletDao(Config domaConfig) {
        return new WalletDaoImpl(domaConfig);
    }

    @Bean
    public CategoryDao categoryDao(Config domaConfig) {
        return new CategoryDaoImpl(domaConfig);
    }

    @Bean
    public TransactionDao transactionDao(Config domaConfig) {
        return new TransactionDaoImpl(domaConfig);
    }

    @Bean
    public BudgetDao budgetDao(Config domaConfig) {
        return new BudgetDaoImpl(domaConfig);
    }
}
