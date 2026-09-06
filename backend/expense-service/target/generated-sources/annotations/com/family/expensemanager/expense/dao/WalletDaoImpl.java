package com.family.expensemanager.expense.dao;

/** */
@javax.annotation.processing.Generated(value = { "Doma", "2.61.0" }, date = "2026-09-05T22:41:45.373+0700")
@org.seasar.doma.DaoImplementation
public class WalletDaoImpl implements com.family.expensemanager.expense.dao.WalletDao, org.seasar.doma.jdbc.ConfigProvider {

    static {
        org.seasar.doma.internal.Artifact.validateVersion("2.61.0");
    }

    private static final java.lang.reflect.Method __method0 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.WalletDao.class, "insert", com.family.expensemanager.expense.domain.entity.Wallet.class);

    private static final java.lang.reflect.Method __method1 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.WalletDao.class, "selectByFamilyId", java.lang.Long.class);

    private static final java.lang.reflect.Method __method2 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.WalletDao.class, "selectById", java.lang.Long.class);

    private final org.seasar.doma.internal.jdbc.dao.DaoImplSupport __support;

    /**
     * @param config the config
     */
    public WalletDaoImpl(org.seasar.doma.jdbc.Config config) {
        __support = new org.seasar.doma.internal.jdbc.dao.DaoImplSupport(config);
    }

    @Override
    public org.seasar.doma.jdbc.Config getConfig() {
        return __support.getConfig();
    }

    @Override
    public int insert(com.family.expensemanager.expense.domain.entity.Wallet wallet) {
        __support.entering("com.family.expensemanager.expense.dao.WalletDaoImpl", "insert", wallet);
        try {
            if (wallet == null) {
                throw new org.seasar.doma.DomaNullPointerException("wallet");
            }
            org.seasar.doma.jdbc.query.AutoInsertQuery<com.family.expensemanager.expense.domain.entity.Wallet> __query = __support.getQueryImplementors().createAutoInsertQuery(__method0, com.family.expensemanager.expense.domain.entity._Wallet.getSingletonInternal());
            __query.setMethod(__method0);
            __query.setConfig(__support.getConfig());
            __query.setEntity(wallet);
            __query.setDuplicateKeyType(org.seasar.doma.jdbc.query.DuplicateKeyType.EXCEPTION);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.WalletDaoImpl");
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
            __support.exiting("com.family.expensemanager.expense.dao.WalletDaoImpl", "insert", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.WalletDaoImpl", "insert", __e);
            throw __e;
        }
    }

    @Override
    public java.util.List<com.family.expensemanager.expense.domain.entity.Wallet> selectByFamilyId(java.lang.Long familyId) {
        __support.entering("com.family.expensemanager.expense.dao.WalletDaoImpl", "selectByFamilyId", familyId);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method1);
            __query.setMethod(__method1);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/expense/dao/WalletDao/selectByFamilyId.sql");
            __query.setEntityType(com.family.expensemanager.expense.domain.entity._Wallet.getSingletonInternal());
            __query.addParameter("familyId", java.lang.Long.class, familyId);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.WalletDaoImpl");
            __query.setCallerMethodName("selectByFamilyId");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.List<com.family.expensemanager.expense.domain.entity.Wallet>> __command = __support.getCommandImplementors().createSelectCommand(__method1, __query, new org.seasar.doma.internal.jdbc.command.EntityResultListHandler<com.family.expensemanager.expense.domain.entity.Wallet>(com.family.expensemanager.expense.domain.entity._Wallet.getSingletonInternal()));
            java.util.List<com.family.expensemanager.expense.domain.entity.Wallet> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.WalletDaoImpl", "selectByFamilyId", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.WalletDaoImpl", "selectByFamilyId", __e);
            throw __e;
        }
    }

    @Override
    public java.util.Optional<com.family.expensemanager.expense.domain.entity.Wallet> selectById(java.lang.Long id) {
        __support.entering("com.family.expensemanager.expense.dao.WalletDaoImpl", "selectById", id);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method2);
            __query.setMethod(__method2);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/expense/dao/WalletDao/selectById.sql");
            __query.setEntityType(com.family.expensemanager.expense.domain.entity._Wallet.getSingletonInternal());
            __query.addParameter("id", java.lang.Long.class, id);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.WalletDaoImpl");
            __query.setCallerMethodName("selectById");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.Optional<com.family.expensemanager.expense.domain.entity.Wallet>> __command = __support.getCommandImplementors().createSelectCommand(__method2, __query, new org.seasar.doma.internal.jdbc.command.OptionalEntitySingleResultHandler<com.family.expensemanager.expense.domain.entity.Wallet>(com.family.expensemanager.expense.domain.entity._Wallet.getSingletonInternal()));
            java.util.Optional<com.family.expensemanager.expense.domain.entity.Wallet> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.WalletDaoImpl", "selectById", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.WalletDaoImpl", "selectById", __e);
            throw __e;
        }
    }

}
