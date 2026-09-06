package com.family.expensemanager.auth.dao;

/** */
@javax.annotation.processing.Generated(value = { "Doma", "2.61.0" }, date = "2026-09-05T22:41:40.719+0700")
@org.seasar.doma.DaoImplementation
public class RefreshTokenDaoImpl implements com.family.expensemanager.auth.dao.RefreshTokenDao, org.seasar.doma.jdbc.ConfigProvider {

    static {
        org.seasar.doma.internal.Artifact.validateVersion("2.61.0");
    }

    private static final java.lang.reflect.Method __method0 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.auth.dao.RefreshTokenDao.class, "insert", com.family.expensemanager.auth.domain.entity.RefreshToken.class);

    private static final java.lang.reflect.Method __method1 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.auth.dao.RefreshTokenDao.class, "selectByTokenHash", java.lang.String.class);

    private static final java.lang.reflect.Method __method2 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.auth.dao.RefreshTokenDao.class, "update", com.family.expensemanager.auth.domain.entity.RefreshToken.class);

    private final org.seasar.doma.internal.jdbc.dao.DaoImplSupport __support;

    /**
     * @param config the config
     */
    public RefreshTokenDaoImpl(org.seasar.doma.jdbc.Config config) {
        __support = new org.seasar.doma.internal.jdbc.dao.DaoImplSupport(config);
    }

    @Override
    public org.seasar.doma.jdbc.Config getConfig() {
        return __support.getConfig();
    }

    @Override
    public int insert(com.family.expensemanager.auth.domain.entity.RefreshToken refreshToken) {
        __support.entering("com.family.expensemanager.auth.dao.RefreshTokenDaoImpl", "insert", refreshToken);
        try {
            if (refreshToken == null) {
                throw new org.seasar.doma.DomaNullPointerException("refreshToken");
            }
            org.seasar.doma.jdbc.query.AutoInsertQuery<com.family.expensemanager.auth.domain.entity.RefreshToken> __query = __support.getQueryImplementors().createAutoInsertQuery(__method0, com.family.expensemanager.auth.domain.entity._RefreshToken.getSingletonInternal());
            __query.setMethod(__method0);
            __query.setConfig(__support.getConfig());
            __query.setEntity(refreshToken);
            __query.setDuplicateKeyType(org.seasar.doma.jdbc.query.DuplicateKeyType.EXCEPTION);
            __query.setCallerClassName("com.family.expensemanager.auth.dao.RefreshTokenDaoImpl");
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
            __support.exiting("com.family.expensemanager.auth.dao.RefreshTokenDaoImpl", "insert", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.auth.dao.RefreshTokenDaoImpl", "insert", __e);
            throw __e;
        }
    }

    @Override
    public java.util.Optional<com.family.expensemanager.auth.domain.entity.RefreshToken> selectByTokenHash(java.lang.String tokenHash) {
        __support.entering("com.family.expensemanager.auth.dao.RefreshTokenDaoImpl", "selectByTokenHash", tokenHash);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method1);
            __query.setMethod(__method1);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/auth/dao/RefreshTokenDao/selectByTokenHash.sql");
            __query.setEntityType(com.family.expensemanager.auth.domain.entity._RefreshToken.getSingletonInternal());
            __query.addParameter("tokenHash", java.lang.String.class, tokenHash);
            __query.setCallerClassName("com.family.expensemanager.auth.dao.RefreshTokenDaoImpl");
            __query.setCallerMethodName("selectByTokenHash");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.Optional<com.family.expensemanager.auth.domain.entity.RefreshToken>> __command = __support.getCommandImplementors().createSelectCommand(__method1, __query, new org.seasar.doma.internal.jdbc.command.OptionalEntitySingleResultHandler<com.family.expensemanager.auth.domain.entity.RefreshToken>(com.family.expensemanager.auth.domain.entity._RefreshToken.getSingletonInternal()));
            java.util.Optional<com.family.expensemanager.auth.domain.entity.RefreshToken> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.auth.dao.RefreshTokenDaoImpl", "selectByTokenHash", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.auth.dao.RefreshTokenDaoImpl", "selectByTokenHash", __e);
            throw __e;
        }
    }

    @Override
    public int update(com.family.expensemanager.auth.domain.entity.RefreshToken refreshToken) {
        __support.entering("com.family.expensemanager.auth.dao.RefreshTokenDaoImpl", "update", refreshToken);
        try {
            if (refreshToken == null) {
                throw new org.seasar.doma.DomaNullPointerException("refreshToken");
            }
            org.seasar.doma.jdbc.query.AutoUpdateQuery<com.family.expensemanager.auth.domain.entity.RefreshToken> __query = __support.getQueryImplementors().createAutoUpdateQuery(__method2, com.family.expensemanager.auth.domain.entity._RefreshToken.getSingletonInternal());
            __query.setMethod(__method2);
            __query.setConfig(__support.getConfig());
            __query.setEntity(refreshToken);
            __query.setCallerClassName("com.family.expensemanager.auth.dao.RefreshTokenDaoImpl");
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
            org.seasar.doma.jdbc.command.UpdateCommand __command = __support.getCommandImplementors().createUpdateCommand(__method2, __query);
            int __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.auth.dao.RefreshTokenDaoImpl", "update", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.auth.dao.RefreshTokenDaoImpl", "update", __e);
            throw __e;
        }
    }

}
