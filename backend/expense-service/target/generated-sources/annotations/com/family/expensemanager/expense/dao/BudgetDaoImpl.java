package com.family.expensemanager.expense.dao;

/** */
@javax.annotation.processing.Generated(value = { "Doma", "2.61.0" }, date = "2026-09-05T22:41:45.255+0700")
@org.seasar.doma.DaoImplementation
public class BudgetDaoImpl implements com.family.expensemanager.expense.dao.BudgetDao, org.seasar.doma.jdbc.ConfigProvider {

    static {
        org.seasar.doma.internal.Artifact.validateVersion("2.61.0");
    }

    private static final java.lang.reflect.Method __method0 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.BudgetDao.class, "insert", com.family.expensemanager.expense.domain.entity.Budget.class);

    private static final java.lang.reflect.Method __method1 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.BudgetDao.class, "selectByFamilyId", java.lang.Long.class);

    private static final java.lang.reflect.Method __method2 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.BudgetDao.class, "selectByCategoryAndPeriod", java.lang.Long.class, java.lang.String.class);

    private final org.seasar.doma.internal.jdbc.dao.DaoImplSupport __support;

    /**
     * @param config the config
     */
    public BudgetDaoImpl(org.seasar.doma.jdbc.Config config) {
        __support = new org.seasar.doma.internal.jdbc.dao.DaoImplSupport(config);
    }

    @Override
    public org.seasar.doma.jdbc.Config getConfig() {
        return __support.getConfig();
    }

    @Override
    public int insert(com.family.expensemanager.expense.domain.entity.Budget budget) {
        __support.entering("com.family.expensemanager.expense.dao.BudgetDaoImpl", "insert", budget);
        try {
            if (budget == null) {
                throw new org.seasar.doma.DomaNullPointerException("budget");
            }
            org.seasar.doma.jdbc.query.AutoInsertQuery<com.family.expensemanager.expense.domain.entity.Budget> __query = __support.getQueryImplementors().createAutoInsertQuery(__method0, com.family.expensemanager.expense.domain.entity._Budget.getSingletonInternal());
            __query.setMethod(__method0);
            __query.setConfig(__support.getConfig());
            __query.setEntity(budget);
            __query.setDuplicateKeyType(org.seasar.doma.jdbc.query.DuplicateKeyType.EXCEPTION);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.BudgetDaoImpl");
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
            __support.exiting("com.family.expensemanager.expense.dao.BudgetDaoImpl", "insert", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.BudgetDaoImpl", "insert", __e);
            throw __e;
        }
    }

    @Override
    public java.util.List<com.family.expensemanager.expense.domain.entity.Budget> selectByFamilyId(java.lang.Long familyId) {
        __support.entering("com.family.expensemanager.expense.dao.BudgetDaoImpl", "selectByFamilyId", familyId);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method1);
            __query.setMethod(__method1);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/expense/dao/BudgetDao/selectByFamilyId.sql");
            __query.setEntityType(com.family.expensemanager.expense.domain.entity._Budget.getSingletonInternal());
            __query.addParameter("familyId", java.lang.Long.class, familyId);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.BudgetDaoImpl");
            __query.setCallerMethodName("selectByFamilyId");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.List<com.family.expensemanager.expense.domain.entity.Budget>> __command = __support.getCommandImplementors().createSelectCommand(__method1, __query, new org.seasar.doma.internal.jdbc.command.EntityResultListHandler<com.family.expensemanager.expense.domain.entity.Budget>(com.family.expensemanager.expense.domain.entity._Budget.getSingletonInternal()));
            java.util.List<com.family.expensemanager.expense.domain.entity.Budget> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.BudgetDaoImpl", "selectByFamilyId", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.BudgetDaoImpl", "selectByFamilyId", __e);
            throw __e;
        }
    }

    @Override
    public java.util.Optional<com.family.expensemanager.expense.domain.entity.Budget> selectByCategoryAndPeriod(java.lang.Long categoryId, java.lang.String periodMonth) {
        __support.entering("com.family.expensemanager.expense.dao.BudgetDaoImpl", "selectByCategoryAndPeriod", categoryId, periodMonth);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method2);
            __query.setMethod(__method2);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/expense/dao/BudgetDao/selectByCategoryAndPeriod.sql");
            __query.setEntityType(com.family.expensemanager.expense.domain.entity._Budget.getSingletonInternal());
            __query.addParameter("categoryId", java.lang.Long.class, categoryId);
            __query.addParameter("periodMonth", java.lang.String.class, periodMonth);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.BudgetDaoImpl");
            __query.setCallerMethodName("selectByCategoryAndPeriod");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.Optional<com.family.expensemanager.expense.domain.entity.Budget>> __command = __support.getCommandImplementors().createSelectCommand(__method2, __query, new org.seasar.doma.internal.jdbc.command.OptionalEntitySingleResultHandler<com.family.expensemanager.expense.domain.entity.Budget>(com.family.expensemanager.expense.domain.entity._Budget.getSingletonInternal()));
            java.util.Optional<com.family.expensemanager.expense.domain.entity.Budget> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.BudgetDaoImpl", "selectByCategoryAndPeriod", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.BudgetDaoImpl", "selectByCategoryAndPeriod", __e);
            throw __e;
        }
    }

}
