package com.family.expensemanager.expense.dao;

/** */
@javax.annotation.processing.Generated(value = { "Doma", "2.61.0" }, date = "2026-09-05T22:41:45.296+0700")
@org.seasar.doma.DaoImplementation
public class CategoryDaoImpl implements com.family.expensemanager.expense.dao.CategoryDao, org.seasar.doma.jdbc.ConfigProvider {

    static {
        org.seasar.doma.internal.Artifact.validateVersion("2.61.0");
    }

    private static final java.lang.reflect.Method __method0 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.CategoryDao.class, "insert", com.family.expensemanager.expense.domain.entity.Category.class);

    private static final java.lang.reflect.Method __method1 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.CategoryDao.class, "selectByFamilyId", java.lang.Long.class);

    private static final java.lang.reflect.Method __method2 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.CategoryDao.class, "selectByFamilyIdAndType", java.lang.Long.class, java.lang.String.class);

    private static final java.lang.reflect.Method __method3 = org.seasar.doma.internal.jdbc.dao.DaoImplSupport.getDeclaredMethod(com.family.expensemanager.expense.dao.CategoryDao.class, "selectById", java.lang.Long.class);

    private final org.seasar.doma.internal.jdbc.dao.DaoImplSupport __support;

    /**
     * @param config the config
     */
    public CategoryDaoImpl(org.seasar.doma.jdbc.Config config) {
        __support = new org.seasar.doma.internal.jdbc.dao.DaoImplSupport(config);
    }

    @Override
    public org.seasar.doma.jdbc.Config getConfig() {
        return __support.getConfig();
    }

    @Override
    public int insert(com.family.expensemanager.expense.domain.entity.Category category) {
        __support.entering("com.family.expensemanager.expense.dao.CategoryDaoImpl", "insert", category);
        try {
            if (category == null) {
                throw new org.seasar.doma.DomaNullPointerException("category");
            }
            org.seasar.doma.jdbc.query.AutoInsertQuery<com.family.expensemanager.expense.domain.entity.Category> __query = __support.getQueryImplementors().createAutoInsertQuery(__method0, com.family.expensemanager.expense.domain.entity._Category.getSingletonInternal());
            __query.setMethod(__method0);
            __query.setConfig(__support.getConfig());
            __query.setEntity(category);
            __query.setDuplicateKeyType(org.seasar.doma.jdbc.query.DuplicateKeyType.EXCEPTION);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.CategoryDaoImpl");
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
            __support.exiting("com.family.expensemanager.expense.dao.CategoryDaoImpl", "insert", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.CategoryDaoImpl", "insert", __e);
            throw __e;
        }
    }

    @Override
    public java.util.List<com.family.expensemanager.expense.domain.entity.Category> selectByFamilyId(java.lang.Long familyId) {
        __support.entering("com.family.expensemanager.expense.dao.CategoryDaoImpl", "selectByFamilyId", familyId);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method1);
            __query.setMethod(__method1);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/expense/dao/CategoryDao/selectByFamilyId.sql");
            __query.setEntityType(com.family.expensemanager.expense.domain.entity._Category.getSingletonInternal());
            __query.addParameter("familyId", java.lang.Long.class, familyId);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.CategoryDaoImpl");
            __query.setCallerMethodName("selectByFamilyId");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.List<com.family.expensemanager.expense.domain.entity.Category>> __command = __support.getCommandImplementors().createSelectCommand(__method1, __query, new org.seasar.doma.internal.jdbc.command.EntityResultListHandler<com.family.expensemanager.expense.domain.entity.Category>(com.family.expensemanager.expense.domain.entity._Category.getSingletonInternal()));
            java.util.List<com.family.expensemanager.expense.domain.entity.Category> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.CategoryDaoImpl", "selectByFamilyId", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.CategoryDaoImpl", "selectByFamilyId", __e);
            throw __e;
        }
    }

    @Override
    public java.util.List<com.family.expensemanager.expense.domain.entity.Category> selectByFamilyIdAndType(java.lang.Long familyId, java.lang.String type) {
        __support.entering("com.family.expensemanager.expense.dao.CategoryDaoImpl", "selectByFamilyIdAndType", familyId, type);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method2);
            __query.setMethod(__method2);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/expense/dao/CategoryDao/selectByFamilyIdAndType.sql");
            __query.setEntityType(com.family.expensemanager.expense.domain.entity._Category.getSingletonInternal());
            __query.addParameter("familyId", java.lang.Long.class, familyId);
            __query.addParameter("type", java.lang.String.class, type);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.CategoryDaoImpl");
            __query.setCallerMethodName("selectByFamilyIdAndType");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.List<com.family.expensemanager.expense.domain.entity.Category>> __command = __support.getCommandImplementors().createSelectCommand(__method2, __query, new org.seasar.doma.internal.jdbc.command.EntityResultListHandler<com.family.expensemanager.expense.domain.entity.Category>(com.family.expensemanager.expense.domain.entity._Category.getSingletonInternal()));
            java.util.List<com.family.expensemanager.expense.domain.entity.Category> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.CategoryDaoImpl", "selectByFamilyIdAndType", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.CategoryDaoImpl", "selectByFamilyIdAndType", __e);
            throw __e;
        }
    }

    @Override
    public java.util.Optional<com.family.expensemanager.expense.domain.entity.Category> selectById(java.lang.Long id) {
        __support.entering("com.family.expensemanager.expense.dao.CategoryDaoImpl", "selectById", id);
        try {
            org.seasar.doma.jdbc.query.SqlFileSelectQuery __query = __support.getQueryImplementors().createSqlFileSelectQuery(__method3);
            __query.setMethod(__method3);
            __query.setConfig(__support.getConfig());
            __query.setSqlFilePath("META-INF/com/family/expensemanager/expense/dao/CategoryDao/selectById.sql");
            __query.setEntityType(com.family.expensemanager.expense.domain.entity._Category.getSingletonInternal());
            __query.addParameter("id", java.lang.Long.class, id);
            __query.setCallerClassName("com.family.expensemanager.expense.dao.CategoryDaoImpl");
            __query.setCallerMethodName("selectById");
            __query.setResultEnsured(false);
            __query.setResultMappingEnsured(false);
            __query.setFetchType(org.seasar.doma.FetchType.LAZY);
            __query.setQueryTimeout(-1);
            __query.setMaxRows(-1);
            __query.setFetchSize(-1);
            __query.setSqlLogType(org.seasar.doma.jdbc.SqlLogType.FORMATTED);
            __query.prepare();
            org.seasar.doma.jdbc.command.SelectCommand<java.util.Optional<com.family.expensemanager.expense.domain.entity.Category>> __command = __support.getCommandImplementors().createSelectCommand(__method3, __query, new org.seasar.doma.internal.jdbc.command.OptionalEntitySingleResultHandler<com.family.expensemanager.expense.domain.entity.Category>(com.family.expensemanager.expense.domain.entity._Category.getSingletonInternal()));
            java.util.Optional<com.family.expensemanager.expense.domain.entity.Category> __result = __command.execute();
            __query.complete();
            __support.exiting("com.family.expensemanager.expense.dao.CategoryDaoImpl", "selectById", __result);
            return __result;
        } catch (java.lang.RuntimeException __e) {
            __support.throwing("com.family.expensemanager.expense.dao.CategoryDaoImpl", "selectById", __e);
            throw __e;
        }
    }

}
