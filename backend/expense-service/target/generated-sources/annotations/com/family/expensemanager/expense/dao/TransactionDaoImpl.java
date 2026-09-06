package com.family.expensemanager.expense.dao;

/** */
@javax.annotation.processing.Generated(value = { "Doma", "2.61.0" }, date = "2026-09-05T22:41:45.330+0700")
@org.seasar.doma.DaoImplementation
public class TransactionDaoImpl implements com.family.expensemanager.expense.dao.TransactionDao, org.seasar.doma.jdbc.ConfigProvider {

    static {
        org.seasar.doma.internal.Artifact.validateVersion("2.61.0");
    }

    private static final java.lang.reflect.Method __method0 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.TransactionDao.class, "insert", com.family.expensemanager.expense.domain.entity.Transaction.class);

    private static final java.lang.reflect.Method __method1 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.TransactionDao.class, "update", com.family.expensemanager.expense.domain.entity.Transaction.class);

    private static final java.lang.reflect.Method __method2 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.TransactionDao.class, "delete", com.family.expensemanager.expense.domain.entity.Transaction.class);

    private static final java.lang.reflect.Method __method3 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.TransactionDao.class, "selectByFamilyId", java.lang.Long.class);

    private static final java.lang.reflect.Method __method4 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.TransactionDao.class, "selectById", java.lang.Long.class);

    private static final java.lang.reflect.Method __method5 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.TransactionDao.class, "sumAmountByCategoryPeriodAndType", java.lang.Long.class, java.lang.Long.class, java.lang.String.class, java.lang.String.class);

    private static final java.lang.reflect.Method __method6 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.TransactionDao.class, "sumAmountByFamilyPeriodAndType", java.lang.Long.class, java.lang.String.class, java.lang.String.class);

    private final org.seasar.doma.internal.jdbc.dao.DaoImplSupport __support;

    /**
     * @param config the config
     */
    public TransactionDaoImpl(org.seasar.doma.jdbc.Config config) {
        __support = new org.seasar.doma.internal.jdbc.dao.DaoImplSupport(config);
    }

    @Override
    public org.seasar.doma.jdbc.Config getConfig() {
        return __support.getConfig();
    }

    @Override
    public int insert(com.family.expensemanager.expense.domain.entity.Transaction transaction) {
        __support.entering("com.family.expensemanager.expense.dao.TransactionDaoImpl", "insert", transaction);
        try {
            if (transaction == null) {
                throw new org.seasar.doma.DomaNullPointerException("transaction");
            }
            org.seasar.doma.jdbc.query.AutoInsertQuery<com.family.expensemanager.expense.domain.entity.Transaction> __query = __support.getQueryImplementors().createAutoInsertQuery(__method0, com.family.expensemanager.expense.domain.entity._Transaction.getSingletonInternal());
            __query.setMethod(__method0);
            __query.setConfig(__support.getConfig());
            __query.setEntity(transaction);
            __query.setDuplicateKeyType(org.seasar.doma.jdbc.query.DuplicateKeyType.EXCEPTION);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.TransactionDaoImpl");
            __query.setCallerMethodName("insert");
            __query.setQueryTimeout(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.setNullExcluded(false);
            __query.setIncludedPropertyNames();
            __query.setExcludedPropertyNames();
            __query.prepare();
            org.seasar.doma.jdbc.command.InsertCommand __command = __support.getCommandImplementors().createInsertCommand(__method0, __query);
            int __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.TransactionDaoImpl", "insert", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.TransactionDaoImpl", "insert", __e);
            throw __e;
        }
    }

    @Override
    public int update(com.family.expensemanager.expense.domain.entity.Transaction transaction) {
        __support.entering("com.family.expensemanager.expense.dao.TransactionDaoImpl", "update", transaction);
        try {
            if (transaction == null) {
                throw new org.seasar.doma.DomaNullPointerException("transaction");
            }
            org.seasar.doma.jdbc.query.AutoUpdateQuery<com.family.expensemanager.expense.domain.entity.Transaction> __query = __support.getQueryImplementors().createAutoUpdateQuery(__method1, com.family.expensemanager.expense.domain.entity._Transaction.getSingletonInternal());
            __query.setMethod(__method1);
            __query.setConfig(__support.getConfig());
            __query.setEntity(transaction);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.TransactionDaoImpl");
            __query.setCallerMethodName("update");
            __query.setQueryTimeout(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.setNullExcluded(false);
            __query.setVersionIgnored(false);
            __query.setIncludedPropertyNames();
            __query.setExcludedPropertyNames();
            __query.setUnchangedPropertyIncluded(false);
            __query.setOptimisticLockExceptionSuppressed(false);
            __query.prepare();
            org.seasar.doma.jdbc.command.UpdateCommand __command = __support.getCommandImplementors().createUpdateCommand(__method1, __query);
            int __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.TransactionDaoImpl", "update", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.TransactionDaoImpl", "update", __e);
            throw __e;
        }
    }

    @Override
    public int delete(com.family.expensemanager.expense.domain.entity.Transaction transaction) {
        __support.entering("com.family.expensemanager.expense.dao.TransactionDaoImpl", "delete", transaction);
        try {
            if (transaction == null) {
                throw new org.seasar.doma.DomaNullPointerException("transaction");
            }
            org.seasar.doma.jdbc.query.AutoDeleteQuery<com.family.expensemanager.expense.domain.entity.Transaction> __query = __support.getQueryImplementors().createAutoDeleteQuery(__method2, com.family.expensemanager.expense.domain.entity._Transaction.getSingletonInternal());
            __query.setMethod(__method2);
            __query.setConfig(__support.getConfig());
            __query.setEntity(transaction);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.TransactionDaoImpl");
            __query.setCallerMethodName("delete");
            __query.setQueryTimeout(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.setVersionIgnored(false);
            __query.setOptimisticLockExceptionSuppressed(false);
            __query.prepare();
            org.seasar.doma.jdbc.command.DeleteCommand __command = __support.getCommandImplementors().createDeleteCommand(__method2, __query);
            int __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.TransactionDaoImpl", "delete", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.TransactionDaoImpl", "delete", __e);
            throw __e;
        }
    }

    @Override
    public java.util.List<com.family.expensemanager.expense.domain.entity.Transaction> selectByFamilyId(java.lang.Long familyId) {
        __support.entering("com.family.expensemanager.expense.dao.TransactionDaoImpl", "selectByFamilyId", familyId);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method3);
            __query.setMethod(__method3);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/expense/dao/TransactionDao/selectByFamilyId.sql");
            __query.setEntityType(com.family.expensemanager.expense.domain.entity._Transaction.getSingletonInternal());
            __query.addParameter("familyId", java.lang.Long.class, familyId);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.TransactionDaoImpl");
            __query.setCallerMethodName("selectByFamilyId");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.List<com.family.expensemanager.expense.domain.entity.Transaction>> __command = __support.getCommandImplementors().createSelectCommand(__method3, __query, new org.seasar.doma.internal.jdbc.command.EntityResultListHandler<com.family.expensemanager.expense.domain.entity.Transaction>(com.family.expensemanager.expense.domain.entity._Transaction.getSingletonInternal()));
            java.util.List<com.family.expensemanager.expense.domain.entity.Transaction> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.TransactionDaoImpl", "selectByFamilyId", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.TransactionDaoImpl", "selectByFamilyId", __e);
            throw __e;
        }
    }

    @Override
    public java.util.Optional<com.family.expensemanager.expense.domain.entity.Transaction> selectById(java.lang.Long id) {
        __support.entering("com.family.expensemanager.expense.dao.TransactionDaoImpl", "selectById", id);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method4);
            __query.setMethod(__method4);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/expense/dao/TransactionDao/selectById.sql");
            __query.setEntityType(com.family.expensemanager.expense.domain.entity._Transaction.getSingletonInternal());
            __query.addParameter("id", java.lang.Long.class, id);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.TransactionDaoImpl");
            __query.setCallerMethodName("selectById");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.Optional<com.family.expensemanager.expense.domain.entity.Transaction>> __command = __support.getCommandImplementors().createSelectCommand(__method4, __query, new org.seasar.doma.internal.jdbc.command.OptionalEntitySingleResultHandler<com.family.expensemanager.expense.domain.entity.Transaction>(com.family.expensemanager.expense.domain.entity._Transaction.getSingletonInternal()));
            java.util.Optional<com.family.expensemanager.expense.domain.entity.Transaction> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.TransactionDaoImpl", "selectById", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.TransactionDaoImpl", "selectById", __e);
            throw __e;
        }
    }

    @Override
    public java.math.BigDecimal sumAmountByCategoryPeriodAndType(java.lang.Long familyId, java.lang.Long categoryId, java.lang.String periodMonth, java.lang.String type) {
        __support.entering("com.family.expensemanager.expense.dao.TransactionDaoImpl", "sumAmountByCategoryPeriodAndType", familyId, categoryId, periodMonth, type);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method5);
            __query.setMethod(__method5);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/expense/dao/TransactionDao/sumAmountByCategoryPeriodAndType.sql");
            __query.addParameter("familyId", java.lang.Long.class, familyId);
            __query.addParameter("categoryId", java.lang.Long.class, categoryId);
            __query.addParameter("periodMonth", java.lang.String.class, periodMonth);
            __query.addParameter("type", java.lang.String.class, type);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.TransactionDaoImpl");
            __query.setCallerMethodName("sumAmountByCategoryPeriodAndType");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.math.BigDecimal> __command = __support.getCommandImplementors().createSelectCommand(__method5, __query, new org.seasar.doma.internal.jdbc.command.BasicSingleResultHandler<java.math.BigDecimal>(org.seasar.doma.internal.wrapper.WrapperSuppliers.ofBigDecimal()));
            java.math.BigDecimal __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.TransactionDaoImpl", "sumAmountByCategoryPeriodAndType", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.TransactionDaoImpl", "sumAmountByCategoryPeriodAndType", __e);
            throw __e;
        }
    }

    @Override
    public java.math.BigDecimal sumAmountByFamilyPeriodAndType(java.lang.Long familyId, java.lang.String periodMonth, java.lang.String type) {
        __support.entering("com.family.expensemanager.expense.dao.TransactionDaoImpl", "sumAmountByFamilyPeriodAndType", familyId, periodMonth, type);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method6);
            __query.setMethod(__method6);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/expense/dao/TransactionDao/sumAmountByFamilyPeriodAndType.sql");
            __query.addParameter("familyId", java.lang.Long.class, familyId);
            __query.addParameter("periodMonth", java.lang.String.class, periodMonth);
            __query.addParameter("type", java.lang.String.class, type);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.TransactionDaoImpl");
            __query.setCallerMethodName("sumAmountByFamilyPeriodAndType");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.math.BigDecimal> __command = __support.getCommandImplementors().createSelectCommand(__method6, __query, new org.seasar.doma.internal.jdbc.command.BasicSingleResultHandler<java.math.BigDecimal>(org.seasar.doma.internal.wrapper.WrapperSuppliers.ofBigDecimal()));
            java.math.BigDecimal __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.TransactionDaoImpl", "sumAmountByFamilyPeriodAndType", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.TransactionDaoImpl", "sumAmountByFamilyPeriodAndType", __e);
            throw __e;
        }
    }

}
