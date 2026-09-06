package com.family.expensemanager.auth.dao;

/** */
@javax.annotation.processing.Generated(value = { "Doma", "2.61.0" }, date = "2026-09-06T14:09:04.256+0700")
@org.seasar.doma.DaoImplementation
public class FamilyDaoImpl implements com.family.expensemanager.auth.dao.FamilyDao, org.seasar.doma.jdbc.ConfigProvider {

    static {
        org.seasar.doma.internal.Artifact.validateVersion("2.61.0");
    }

    private static final java.lang.reflect.Method __method0 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.auth.dao.FamilyDao.class, "insert", com.family.expensemanager.auth.domain.entity.Family.class);

    private final org.seasar.doma.internal.jdbc.dao.DaoImplSupport __support;

    /**
     * @param config the config
     */
    public FamilyDaoImpl(org.seasar.doma.jdbc.Config config) {
        __support = new org.seasar.doma.internal.jdbc.dao.DaoImplSupport(config);
    }

    @Override
    public org.seasar.doma.jdbc.Config getConfig() {
        return __support.getConfig();
    }

    @Override
    public int insert(com.family.expensemanager.auth.domain.entity.Family family) {
        __support.entering("com.family.expensemanager.auth.dao.FamilyDaoImpl", "insert", family);
        try {
            if (family == null) {
                throw new org.seasar.doma.DomaNullPointerException("family");
            }
            org.seasar.doma.jdbc.query.AutoInsertQuery<com.family.expensemanager.auth.domain.entity.Family> __query = __support.getQueryImplementors().createAutoInsertQuery(__method0, com.family.expensemanager.auth.domain.entity._Family.getSingletonInternal());
            __query.setMethod(__method0);
            __query.setConfig(__support.getConfig());
            __query.setEntity(family);
            __query.setDuplicateKeyType(org.seasar.doma.jdbc.query.DuplicateKeyType.EXCEPTION);
            __query.setCallerClassName("com.family.expensemanager.auth.dao.FamilyDaoImpl");
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
            __support.exiting("com.family.expensemanager.auth.dao.FamilyDaoImpl", "insert", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.auth.dao.FamilyDaoImpl", "insert", __e);
            throw __e;
        }
    }

}
