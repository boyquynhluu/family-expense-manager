package com.family.expensemanager.batch.config;

import javax.sql.DataSource;

import org.seasar.doma.jdbc.Config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.family.expensemanager.batch.dao.WalletBalanceDao;
import com.family.expensemanager.batch.dao.WalletBalanceDaoImpl;

/**
 * Doma DAOs of the batch module. They read fem_expense outside the job's transactions (those belong to fem_batch),
 * so each SELECT simply auto-commits — no TransactionAwareDataSourceProxy as in the services.
 *
 * @author boyquynhluu
 */
@Configuration
public class DomaConfiguration {

    @Bean
    public Config expenseDomaConfig(@Qualifier("expenseDataSource") DataSource expenseDataSource) {
        return new BatchDomaConfig(expenseDataSource);
    }

    @Bean
    public WalletBalanceDao walletBalanceDao(@Qualifier("expenseDomaConfig") Config expenseDomaConfig) {
        return new WalletBalanceDaoImpl(expenseDomaConfig);
    }
}
