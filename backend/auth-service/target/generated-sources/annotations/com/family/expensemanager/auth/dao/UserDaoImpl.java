package com.family.expensemanager.auth.dao;

/** */
@javax.annotation.processing.Generated(value = { "Doma", "2.61.0" }, date = "2026-09-05T22:41:40.739+0700")
@org.seasar.doma.DaoImplementation
public class UserDaoImpl implements com.family.expensemanager.auth.dao.UserDao, org.seasar.doma.jdbc.ConfigProvider {

    static {
        org.seasar.doma.internal.Artifact.validateVersion("2.61.0");
    }

    private static final java.lang.reflect.Method __method0 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.auth.dao.UserDao.class, "insert", com.family.expensemanager.auth.domain.entity.User.class);

    private static final java.lang.reflect.Method __method1 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.auth.dao.UserDao.class, "selectByEmail", java.lang.String.class);

    private static final java.lang.reflect.Method __method2 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.auth.dao.UserDao.class, "selectById", java.lang.Long.class);

    private final org.seasar.doma.internal.jdbc.dao.DaoImplSupport __support;

    /**
     * @param config the config
     */
    public UserDaoImpl(org.seasar.doma.jdbc.Config config) {
        __support = new org.seasar.doma.internal.jdbc.dao.DaoImplSupport(config);
    }

    @Override
    public org.seasar.doma.jdbc.Config getConfig() {
        return __support.getConfig();
    }

    @Override
    public int insert(com.family.expensemanager.auth.domain.entity.User user) {
        __support.entering("com.family.expensemanager.auth.dao.UserDaoImpl", "insert", user);
        try {
            if (user == null) {
                throw new org.seasar.doma.DomaNullPointerException("user");
            }
            org.seasar.doma.jdbc.query.AutoInsertQuery<com.family.expensemanager.auth.domain.entity.User> __query = __support.getQueryImplementors().createAutoInsertQuery(__method0, com.family.expensemanager.auth.domain.entity._User.getSingletonInternal());
            __query.setMethod(__method0);
            __query.setConfig(__support.getConfig());
            __query.setEntity(user);
            __query.setDuplicateKeyType(org.seasar.doma.jdbc.query.DuplicateKeyType.EXCEPTION);
            __query.setCallerClassName("com.family.expensemanager.auth.dao.UserDaoImpl");
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
            __support.exiting("com.family.expensemanager.auth.dao.UserDaoImpl", "insert", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.auth.dao.UserDaoImpl", "insert", __e);
            throw __e;
        }
    }

    @Override
    public java.util.Optional<com.family.expensemanager.auth.domain.entity.User> selectByEmail(java.lang.String email) {
        __support.entering("com.family.expensemanager.auth.dao.UserDaoImpl", "selectByEmail", email);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method1);
            __query.setMethod(__method1);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/auth/dao/UserDao/selectByEmail.sql");
            __query.setEntityType(com.family.expensemanager.auth.domain.entity._User.getSingletonInternal());
            __query.addParameter("email", java.lang.String.class, email);
            __query.setCallerClassName("com.family.expensemanager.auth.dao.UserDaoImpl");
            __query.setCallerMethodName("selectByEmail");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.Optional<com.family.expensemanager.auth.domain.entity.User>> __command = __support.getCommandImplementors().createSelectCommand(__method1, __query, new org.seasar.doma.internal.jdbc.command.OptionalEntitySingleResultHandler<com.family.expensemanager.auth.domain.entity.User>(com.family.expensemanager.auth.domain.entity._User.getSingletonInternal()));
            java.util.Optional<com.family.expensemanager.auth.domain.entity.User> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.auth.dao.UserDaoImpl", "selectByEmail", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.auth.dao.UserDaoImpl", "selectByEmail", __e);
            throw __e;
        }
    }

    @Override
    public java.util.Optional<com.family.expensemanager.auth.domain.entity.User> selectById(java.lang.Long id) {
        __support.entering("com.family.expensemanager.auth.dao.UserDaoImpl", "selectById", id);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method2);
            __query.setMethod(__method2);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/auth/dao/UserDao/selectById.sql");
            __query.setEntityType(com.family.expensemanager.auth.domain.entity._User.getSingletonInternal());
            __query.addParameter("id", java.lang.Long.class, id);
            __query.setCallerClassName("com.family.expensemanager.auth.dao.UserDaoImpl");
            __query.setCallerMethodName("selectById");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.Optional<com.family.expensemanager.auth.domain.entity.User>> __command = __support.getCommandImplementors().createSelectCommand(__method2, __query, new org.seasar.doma.internal.jdbc.command.OptionalEntitySingleResultHandler<com.family.expensemanager.auth.domain.entity.User>(com.family.expensemanager.auth.domain.entity._User.getSingletonInternal()));
            java.util.Optional<com.family.expensemanager.auth.domain.entity.User> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.auth.dao.UserDaoImpl", "selectById", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.auth.dao.UserDaoImpl", "selectById", __e);
            throw __e;
        }
    }

}
