package com.family.expensemanager.expense.config;

import com.family.expensemanager.common.doma.AppDomaConfig;
import com.family.expensemanager.expense.dao.EntityAuditLogDao;
import com.family.expensemanager.expense.dao.EntityAuditLogDaoImpl;
import com.family.expensemanager.expense.dao.RecurringDraftDao;
import com.family.expensemanager.expense.dao.RecurringDraftDaoImpl;
import com.family.expensemanager.expense.dao.MemberSpendingLimitDao;
import com.family.expensemanager.expense.dao.MemberSpendingLimitDaoImpl;
import com.family.expensemanager.expense.dao.FamilySettingDao;
import com.family.expensemanager.expense.dao.FamilySettingDaoImpl;
import com.family.expensemanager.expense.dao.TransactionApprovalDao;
import com.family.expensemanager.expense.dao.TransactionApprovalDaoImpl;
import com.family.expensemanager.expense.dao.LoanDao;
import com.family.expensemanager.expense.dao.LoanDaoImpl;
import com.family.expensemanager.expense.dao.LoanPaymentDao;
import com.family.expensemanager.expense.dao.LoanPaymentDaoImpl;
import com.family.expensemanager.expense.dao.SavingsGoalDao;
import com.family.expensemanager.expense.dao.SavingsGoalDaoImpl;
import com.family.expensemanager.expense.dao.MonthlySummaryRunDao;
import com.family.expensemanager.expense.dao.MonthlySummaryRunDaoImpl;
import com.family.expensemanager.expense.dao.TransactionSplitDao;
import com.family.expensemanager.expense.dao.TransactionSplitDaoImpl;
import com.family.expensemanager.expense.dao.TagDao;
import com.family.expensemanager.expense.dao.TagDaoImpl;
import com.family.expensemanager.expense.dao.BudgetDao;
import com.family.expensemanager.expense.dao.BudgetDaoImpl;
import com.family.expensemanager.expense.dao.CategoryDao;
import com.family.expensemanager.expense.dao.CategoryDaoImpl;
import com.family.expensemanager.expense.dao.IdempotencyKeyDao;
import com.family.expensemanager.expense.dao.IdempotencyKeyDaoImpl;
import com.family.expensemanager.expense.dao.PeriodLockDao;
import com.family.expensemanager.expense.dao.PeriodLockDaoImpl;
import com.family.expensemanager.expense.dao.PeriodLockLogDao;
import com.family.expensemanager.expense.dao.PeriodLockLogDaoImpl;
import com.family.expensemanager.expense.dao.RecurringTransactionDao;
import com.family.expensemanager.expense.dao.RecurringTransactionDaoImpl;
import com.family.expensemanager.expense.dao.ReportDao;
import com.family.expensemanager.expense.dao.ReportDaoImpl;
import com.family.expensemanager.expense.dao.TransactionAuditLogDao;
import com.family.expensemanager.expense.dao.TransactionAuditLogDaoImpl;
import com.family.expensemanager.expense.dao.TransactionDao;
import com.family.expensemanager.expense.dao.TransactionDaoImpl;
import com.family.expensemanager.expense.dao.TransferRequestDao;
import com.family.expensemanager.expense.dao.TransferRequestDaoImpl;
import com.family.expensemanager.expense.dao.WalletAdjustmentDao;
import com.family.expensemanager.expense.dao.WalletAdjustmentDaoImpl;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.dao.WalletDaoImpl;
import com.family.expensemanager.expense.dao.WalletTransferDao;
import com.family.expensemanager.expense.dao.WalletTransferDaoImpl;
import org.seasar.doma.jdbc.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;

/**
 * @author boyquynhluu
 */
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

    @Bean
    public RecurringTransactionDao recurringTransactionDao(Config domaConfig) {
        return new RecurringTransactionDaoImpl(domaConfig);
    }

    @Bean
    public WalletTransferDao walletTransferDao(Config domaConfig) {
        return new WalletTransferDaoImpl(domaConfig);
    }

    @Bean
    public TransferRequestDao transferRequestDao(Config domaConfig) {
        return new TransferRequestDaoImpl(domaConfig);
    }

    @Bean
    public ReportDao reportDao(Config domaConfig) {
        return new ReportDaoImpl(domaConfig);
    }

    @Bean
    public IdempotencyKeyDao idempotencyKeyDao(Config domaConfig) {
        return new IdempotencyKeyDaoImpl(domaConfig);
    }

    @Bean
    public TransactionAuditLogDao transactionAuditLogDao(Config domaConfig) {
        return new TransactionAuditLogDaoImpl(domaConfig);
    }

    @Bean
    public WalletAdjustmentDao walletAdjustmentDao(Config domaConfig) {
        return new WalletAdjustmentDaoImpl(domaConfig);
    }

    @Bean
    public PeriodLockDao periodLockDao(Config domaConfig) {
        return new PeriodLockDaoImpl(domaConfig);
    }

    @Bean
    public PeriodLockLogDao periodLockLogDao(Config domaConfig) {
        return new PeriodLockLogDaoImpl(domaConfig);
    }

    @Bean
    public EntityAuditLogDao entityAuditLogDao(Config domaConfig) {
        return new EntityAuditLogDaoImpl(domaConfig);
    }

    @Bean
    public RecurringDraftDao recurringDraftDao(Config domaConfig) {
        return new RecurringDraftDaoImpl(domaConfig);
    }

    @Bean
    public MemberSpendingLimitDao memberSpendingLimitDao(Config domaConfig) {
        return new MemberSpendingLimitDaoImpl(domaConfig);
    }

    @Bean
    public FamilySettingDao familySettingDao(Config domaConfig) {
        return new FamilySettingDaoImpl(domaConfig);
    }

    @Bean
    public TransactionApprovalDao transactionApprovalDao(Config domaConfig) {
        return new TransactionApprovalDaoImpl(domaConfig);
    }

    @Bean
    public LoanDao loanDao(Config domaConfig) {
        return new LoanDaoImpl(domaConfig);
    }

    @Bean
    public LoanPaymentDao loanPaymentDao(Config domaConfig) {
        return new LoanPaymentDaoImpl(domaConfig);
    }

    @Bean
    public SavingsGoalDao savingsGoalDao(Config domaConfig) {
        return new SavingsGoalDaoImpl(domaConfig);
    }

    @Bean
    public MonthlySummaryRunDao monthlySummaryRunDao(Config domaConfig) {
        return new MonthlySummaryRunDaoImpl(domaConfig);
    }

    @Bean
    public TransactionSplitDao transactionSplitDao(Config domaConfig) {
        return new TransactionSplitDaoImpl(domaConfig);
    }

    @Bean
    public TagDao tagDao(Config domaConfig) {
        return new TagDaoImpl(domaConfig);
    }
}
