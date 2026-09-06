package com.family.expensemanager.notification.dao;

/** */
@javax.annotation.processing.Generated(value = { "Doma", "2.61.0" }, date = "2026-09-05T22:41:51.079+0700")
@org.seasar.doma.DaoImplementation
public class NotificationDaoImpl implements com.family.expensemanager.notification.dao.NotificationDao, org.seasar.doma.jdbc.ConfigProvider {

    static {
        org.seasar.doma.internal.Artifact.validateVersion("2.61.0");
    }

    private static final java.lang.reflect.Method __method0 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.notification.dao.NotificationDao.class, "insert", com.family.expensemanager.notification.domain.entity.Notification.class);

    private static final java.lang.reflect.Method __method1 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.notification.dao.NotificationDao.class, "selectByFamilyId", java.lang.Long.class);

    private final org.seasar.doma.internal.jdbc.dao.DaoImplSupport __support;

    /**
     * @param config the config
     */
    public NotificationDaoImpl(org.seasar.doma.jdbc.Config config) {
        __support = new org.seasar.doma.internal.jdbc.dao.DaoImplSupport(config);
    }

    @Override
    public org.seasar.doma.jdbc.Config getConfig() {
        return __support.getConfig();
    }

    @Override
    public int insert(com.family.expensemanager.notification.domain.entity.Notification notification) {
        __support.entering("com.family.expensemanager.notification.dao.NotificationDaoImpl", "insert", notification);
        try {
            if (notification == null) {
                throw new org.seasar.doma.DomaNullPointerException("notification");
            }
            org.seasar.doma.jdbc.query.AutoInsertQuery<com.family.expensemanager.notification.domain.entity.Notification> __query = __support.getQueryImplementors().createAutoInsertQuery(__method0, com.family.expensemanager.notification.domain.entity._Notification.getSingletonInternal());
            __query.setMethod(__method0);
            __query.setConfig(__support.getConfig());
            __query.setEntity(notification);
            __query.setDuplicateKeyType(org.seasar.doma.jdbc.query.DuplicateKeyType.EXCEPTION);
            __query.setCallerClassName("com.family.expensemanager.notification.dao.NotificationDaoImpl");
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
            __support.exiting("com.family.expensemanager.notification.dao.NotificationDaoImpl", "insert", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.notification.dao.NotificationDaoImpl", "insert", __e);
            throw __e;
        }
    }

    @Override
    public java.util.List<com.family.expensemanager.notification.domain.entity.Notification> selectByFamilyId(java.lang.Long familyId) {
        __support.entering("com.family.expensemanager.notification.dao.NotificationDaoImpl", "selectByFamilyId", familyId);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method1);
            __query.setMethod(__method1);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/notification/dao/NotificationDao/selectByFamilyId.sql");
            __query.setEntityType(com.family.expensemanager.notification.domain.entity._Notification.getSingletonInternal());
            __query.addParameter("familyId", java.lang.Long.class, familyId);
            __query.setCallerClassName("com.family.expensemanager.notification.dao.NotificationDaoImpl");
            __query.setCallerMethodName("selectByFamilyId");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.List<com.family.expensemanager.notification.domain.entity.Notification>> __command = __support.getCommandImplementors().createSelectCommand(__method1, __query, new org.seasar.doma.internal.jdbc.command.EntityResultListHandler<com.family.expensemanager.notification.domain.entity.Notification>(com.family.expensemanager.notification.domain.entity._Notification.getSingletonInternal()));
            java.util.List<com.family.expensemanager.notification.domain.entity.Notification> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.notification.dao.NotificationDaoImpl", "selectByFamilyId", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.notification.dao.NotificationDaoImpl", "selectByFamilyId", __e);
            throw __e;
        }
    }

}
