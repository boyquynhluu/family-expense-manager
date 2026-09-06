package com.family.expensemanager.expense.domain.entity;

/** */
@javax.annotation.processing.Generated(value = { "Doma", "2.61.0" }, date = "2026-09-06T14:09:12.508+0700")
@org.seasar.doma.EntityTypeImplementation
public final class _Category extends org.seasar.doma.jdbc.entity.AbstractEntityType<com.family.expensemanager.expense.domain.entity.Category> {

    static {
        org.seasar.doma.internal.Artifact.validateVersion("2.61.0");
    }

    private static final _Category __singleton = new _Category();

    private final org.seasar.doma.jdbc.entity.NamingType __namingType = null;

    private final org.seasar.doma.jdbc.id.BuiltinIdentityIdGenerator __idGenerator = new org.seasar.doma.jdbc.id.BuiltinIdentityIdGenerator();

    private final java.util.function.Supplier<org.seasar.doma.jdbc.entity.NullEntityListener<com.family.expensemanager.expense.domain.entity.Category>> __listenerSupplier;

    private final boolean __immutable;

    private final String __catalogName;

    private final String __schemaName;

    private final String __tableName;

    private final boolean __isQuoteRequired;

    private final String __name;

    private final java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __idPropertyTypes;

    private final java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __entityPropertyTypes;

    private final java.util.Map<String, org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __entityPropertyTypeMap;

    @SuppressWarnings("unused")
    private final java.util.Map<String, org.seasar.doma.jdbc.entity.EmbeddedPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __embeddedPropertyTypeMap;

    private _Category() {
        __listenerSupplier = org.seasar.doma.internal.jdbc.entity.NullEntityListenerSuppliers.of();
        __immutable = false;
        __name = "Category";
        __catalogName = "";
        __schemaName = "";
        __tableName = "CATEGORIES";
        __isQuoteRequired = false;
        java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __idList = new java.util.ArrayList<>();
        java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __list = new java.util.ArrayList<>(6);
        java.util.Map<String, org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __map = new java.util.LinkedHashMap<>(6);
        java.util.Map<String, org.seasar.doma.jdbc.entity.EmbeddedPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __embeddedMap = new java.util.LinkedHashMap<>(6);
        initializeMaps(__map, __embeddedMap);
        initializeIdList(__map, __idList);
        initializeList(__map, __list);
        __idPropertyTypes = java.util.Collections.unmodifiableList(__idList);
        __entityPropertyTypes = java.util.Collections.unmodifiableList(__list);
        __entityPropertyTypeMap = java.util.Collections.unmodifiableMap(__map);
        __embeddedPropertyTypeMap = java.util.Collections.unmodifiableMap(__embeddedMap);
    }

    private void initializeMaps(java.util.Map<String, org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __map, java.util.Map<String, org.seasar.doma.jdbc.entity.EmbeddedPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __embeddedMap) {
        __map.put("id", new org.seasar.doma.jdbc.entity.GeneratedIdPropertyType<com.family.expensemanager.expense.domain.entity.Category, java.lang.Long, java.lang.Long>(com.family.expensemanager.expense.domain.entity.Category.class, org.seasar.doma.internal.jdbc.scalar.BasicScalarSuppliers.ofLong(), "id", "", __namingType, false, __idGenerator));
        __map.put("familyId", new org.seasar.doma.jdbc.entity.DefaultPropertyType<com.family.expensemanager.expense.domain.entity.Category, java.lang.Long, java.lang.Long>(com.family.expensemanager.expense.domain.entity.Category.class, org.seasar.doma.internal.jdbc.scalar.BasicScalarSuppliers.ofLong(), "familyId", "family_id", __namingType, true, true, false));
        __map.put("name", new org.seasar.doma.jdbc.entity.DefaultPropertyType<com.family.expensemanager.expense.domain.entity.Category, java.lang.String, java.lang.String>(com.family.expensemanager.expense.domain.entity.Category.class, org.seasar.doma.internal.jdbc.scalar.BasicScalarSuppliers.ofString(), "name", "", __namingType, true, true, false));
        __map.put("type", new org.seasar.doma.jdbc.entity.DefaultPropertyType<com.family.expensemanager.expense.domain.entity.Category, java.lang.String, java.lang.String>(com.family.expensemanager.expense.domain.entity.Category.class, org.seasar.doma.internal.jdbc.scalar.BasicScalarSuppliers.ofString(), "type", "", __namingType, true, true, false));
        __map.put("icon", new org.seasar.doma.jdbc.entity.DefaultPropertyType<com.family.expensemanager.expense.domain.entity.Category, java.lang.String, java.lang.String>(com.family.expensemanager.expense.domain.entity.Category.class, org.seasar.doma.internal.jdbc.scalar.BasicScalarSuppliers.ofString(), "icon", "", __namingType, true, true, false));
        __map.put("color", new org.seasar.doma.jdbc.entity.DefaultPropertyType<com.family.expensemanager.expense.domain.entity.Category, java.lang.String, java.lang.String>(com.family.expensemanager.expense.domain.entity.Category.class, org.seasar.doma.internal.jdbc.scalar.BasicScalarSuppliers.ofString(), "color", "", __namingType, true, true, false));
    }

    private void initializeIdList(java.util.Map<String, org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __map, java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __idList) {
        __idList.add(__map.get("id"));
    }

    private void initializeList(java.util.Map<String, org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __map, java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> __list) {
        __list.addAll(__map.values());
    }

    @Override
    public org.seasar.doma.jdbc.entity.NamingType getNamingType() {
        return __namingType;
    }

    @Override
    public boolean isImmutable() {
        return __immutable;
    }

    @Override
    public String getName() {
        return __name;
    }

    @Override
    public String getCatalogName() {
        return __catalogName;
    }

    @Override
    public String getSchemaName() {
        return __schemaName;
    }

    @Override
    @Deprecated
    public String getTableName() {
        return getTableName(org.seasar.doma.internal.jdbc.entity.TableNames.namingFunction);
    }

    @Override
    public String getTableName(java.util.function.BiFunction<org.seasar.doma.jdbc.entity.NamingType, String, String> namingFunction) {
        if (__tableName.isEmpty()) {
            return namingFunction.apply(__namingType, __name);
        }
        return __tableName;
    }

    @Override
    public boolean isQuoteRequired() {
        return __isQuoteRequired;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void preInsert(com.family.expensemanager.expense.domain.entity.Category entity, org.seasar.doma.jdbc.entity.PreInsertContext<com.family.expensemanager.expense.domain.entity.Category> context) {
        Class __listenerClass = org.seasar.doma.jdbc.entity.NullEntityListener.class;
        org.seasar.doma.jdbc.entity.NullEntityListener __listener = context.getConfig().getEntityListenerProvider().get(__listenerClass, __listenerSupplier);
        __listener.preInsert(entity, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void preUpdate(com.family.expensemanager.expense.domain.entity.Category entity, org.seasar.doma.jdbc.entity.PreUpdateContext<com.family.expensemanager.expense.domain.entity.Category> context) {
        Class __listenerClass = org.seasar.doma.jdbc.entity.NullEntityListener.class;
        org.seasar.doma.jdbc.entity.NullEntityListener __listener = context.getConfig().getEntityListenerProvider().get(__listenerClass, __listenerSupplier);
        __listener.preUpdate(entity, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void preDelete(com.family.expensemanager.expense.domain.entity.Category entity, org.seasar.doma.jdbc.entity.PreDeleteContext<com.family.expensemanager.expense.domain.entity.Category> context) {
        Class __listenerClass = org.seasar.doma.jdbc.entity.NullEntityListener.class;
        org.seasar.doma.jdbc.entity.NullEntityListener __listener = context.getConfig().getEntityListenerProvider().get(__listenerClass, __listenerSupplier);
        __listener.preDelete(entity, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void postInsert(com.family.expensemanager.expense.domain.entity.Category entity, org.seasar.doma.jdbc.entity.PostInsertContext<com.family.expensemanager.expense.domain.entity.Category> context) {
        Class __listenerClass = org.seasar.doma.jdbc.entity.NullEntityListener.class;
        org.seasar.doma.jdbc.entity.NullEntityListener __listener = context.getConfig().getEntityListenerProvider().get(__listenerClass, __listenerSupplier);
        __listener.postInsert(entity, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void postUpdate(com.family.expensemanager.expense.domain.entity.Category entity, org.seasar.doma.jdbc.entity.PostUpdateContext<com.family.expensemanager.expense.domain.entity.Category> context) {
        Class __listenerClass = org.seasar.doma.jdbc.entity.NullEntityListener.class;
        org.seasar.doma.jdbc.entity.NullEntityListener __listener = context.getConfig().getEntityListenerProvider().get(__listenerClass, __listenerSupplier);
        __listener.postUpdate(entity, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void postDelete(com.family.expensemanager.expense.domain.entity.Category entity, org.seasar.doma.jdbc.entity.PostDeleteContext<com.family.expensemanager.expense.domain.entity.Category> context) {
        Class __listenerClass = org.seasar.doma.jdbc.entity.NullEntityListener.class;
        org.seasar.doma.jdbc.entity.NullEntityListener __listener = context.getConfig().getEntityListenerProvider().get(__listenerClass, __listenerSupplier);
        __listener.postDelete(entity, context);
    }

    @Override
    public java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> getEntityPropertyTypes() {
        return __entityPropertyTypes;
    }

    @Override
    public org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?> getEntityPropertyType(String __name) {
        return __entityPropertyTypeMap.get(__name);
    }

    @Override
    public java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?>> getIdPropertyTypes() {
        return __idPropertyTypes;
    }

    @SuppressWarnings("unchecked")
    @Override
    public org.seasar.doma.jdbc.entity.GeneratedIdPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?, ?> getGeneratedIdPropertyType() {
        return (org.seasar.doma.jdbc.entity.GeneratedIdPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?, ?>)__entityPropertyTypeMap.get("id");
    }

    @SuppressWarnings("unchecked")
    @Override
    public org.seasar.doma.jdbc.entity.VersionPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?, ?> getVersionPropertyType() {
        return (org.seasar.doma.jdbc.entity.VersionPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?, ?>)__entityPropertyTypeMap.get("null");
    }

    @SuppressWarnings("unchecked")
    @Override
    public org.seasar.doma.jdbc.entity.TenantIdPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?, ?> getTenantIdPropertyType() {
        return (org.seasar.doma.jdbc.entity.TenantIdPropertyType<com.family.expensemanager.expense.domain.entity.Category, ?, ?>)__entityPropertyTypeMap.get("null");
    }

    @Override
    public com.family.expensemanager.expense.domain.entity.Category newEntity(java.util.Map<String, org.seasar.doma.jdbc.entity.Property<com.family.expensemanager.expense.domain.entity.Category, ?>> __args) {
        com.family.expensemanager.expense.domain.entity.Category entity = new com.family.expensemanager.expense.domain.entity.Category();
        if (__args.get("id") != null) __args.get("id").save(entity);
        if (__args.get("familyId") != null) __args.get("familyId").save(entity);
        if (__args.get("name") != null) __args.get("name").save(entity);
        if (__args.get("type") != null) __args.get("type").save(entity);
        if (__args.get("icon") != null) __args.get("icon").save(entity);
        if (__args.get("color") != null) __args.get("color").save(entity);
        return entity;
    }

    @Override
    public Class<com.family.expensemanager.expense.domain.entity.Category> getEntityClass() {
        return com.family.expensemanager.expense.domain.entity.Category.class;
    }

    @Override
    public com.family.expensemanager.expense.domain.entity.Category getOriginalStates(com.family.expensemanager.expense.domain.entity.Category __entity) {
        return null;
    }

    @Override
    public void saveCurrentStates(com.family.expensemanager.expense.domain.entity.Category __entity) {
    }

    /**
     * @return the singleton
     */
    public static _Category getSingletonInternal() {
        return __singleton;
    }

    /**
     * @return the new instance
     */
    public static _Category newInstance() {
        return new _Category();
    }

}
