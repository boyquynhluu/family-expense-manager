package com.family.expensemanager.auth.domain.entity;

/** */
@javax.annotation.processing.Generated(value = { "Doma", "2.61.0" }, date = "2026-09-06T14:09:04.165+0700")
@org.seasar.doma.EntityTypeImplementation
public final class _RefreshToken extends org.seasar.doma.jdbc.entity.AbstractEntityType<com.family.expensemanager.auth.domain.entity.RefreshToken> {

    static {
        org.seasar.doma.internal.Artifact.validateVersion("2.61.0");
    }

    private static final _RefreshToken __singleton = new _RefreshToken();

    private final org.seasar.doma.jdbc.entity.NamingType __namingType = null;

    private final org.seasar.doma.jdbc.id.BuiltinIdentityIdGenerator __idGenerator = new org.seasar.doma.jdbc.id.BuiltinIdentityIdGenerator();

    private final java.util.function.Supplier<org.seasar.doma.jdbc.entity.NullEntityListener<com.family.expensemanager.auth.domain.entity.RefreshToken>> __listenerSupplier;

    private final boolean __immutable;

    private final String __catalogName;

    private final String __schemaName;

    private final String __tableName;

    private final boolean __isQuoteRequired;

    private final String __name;

    private final java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __idPropertyTypes;

    private final java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __entityPropertyTypes;

    private final java.util.Map<String, org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __entityPropertyTypeMap;

    @SuppressWarnings("unused")
    private final java.util.Map<String, org.seasar.doma.jdbc.entity.EmbeddedPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __embeddedPropertyTypeMap;

    private _RefreshToken() {
        __listenerSupplier = org.seasar.doma.internal.jdbc.entity.NullEntityListenerSuppliers.of();
        __immutable = false;
        __name = "RefreshToken";
        __catalogName = "";
        __schemaName = "";
        __tableName = "REFRESH_TOKENS";
        __isQuoteRequired = false;
        java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __idList = new java.util.ArrayList<>();
        java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __list = new java.util.ArrayList<>(5);
        java.util.Map<String, org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __map = new java.util.LinkedHashMap<>(5);
        java.util.Map<String, org.seasar.doma.jdbc.entity.EmbeddedPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __embeddedMap = new java.util.LinkedHashMap<>(5);
        initializeMaps(__map, __embeddedMap);
        initializeIdList(__map, __idList);
        initializeList(__map, __list);
        __idPropertyTypes = java.util.Collections.unmodifiableList(__idList);
        __entityPropertyTypes = java.util.Collections.unmodifiableList(__list);
        __entityPropertyTypeMap = java.util.Collections.unmodifiableMap(__map);
        __embeddedPropertyTypeMap = java.util.Collections.unmodifiableMap(__embeddedMap);
    }

    private void initializeMaps(java.util.Map<String, org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __map, java.util.Map<String, org.seasar.doma.jdbc.entity.EmbeddedPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __embeddedMap) {
        __map.put("id", new org.seasar.doma.jdbc.entity.GeneratedIdPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, java.lang.Long, java.lang.Long>(com.family.expensemanager.auth.domain.entity.RefreshToken.class, org.seasar.doma.internal.jdbc.scalar.BasicScalarSuppliers.ofLong(), "id", "", __namingType, false, __idGenerator));
        __map.put("userId", new org.seasar.doma.jdbc.entity.DefaultPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, java.lang.Long, java.lang.Long>(com.family.expensemanager.auth.domain.entity.RefreshToken.class, org.seasar.doma.internal.jdbc.scalar.BasicScalarSuppliers.ofLong(), "userId", "user_id", __namingType, true, true, false));
        __map.put("tokenHash", new org.seasar.doma.jdbc.entity.DefaultPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, java.lang.String, java.lang.String>(com.family.expensemanager.auth.domain.entity.RefreshToken.class, org.seasar.doma.internal.jdbc.scalar.BasicScalarSuppliers.ofString(), "tokenHash", "token_hash", __namingType, true, true, false));
        __map.put("expiresAt", new org.seasar.doma.jdbc.entity.DefaultPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, java.time.LocalDateTime, java.time.LocalDateTime>(com.family.expensemanager.auth.domain.entity.RefreshToken.class, org.seasar.doma.internal.jdbc.scalar.BasicScalarSuppliers.ofLocalDateTime(), "expiresAt", "expires_at", __namingType, true, true, false));
        __map.put("revoked", new org.seasar.doma.jdbc.entity.DefaultPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, java.lang.Boolean, java.lang.Boolean>(com.family.expensemanager.auth.domain.entity.RefreshToken.class, org.seasar.doma.internal.jdbc.scalar.BasicScalarSuppliers.ofBoolean(), "revoked", "", __namingType, true, true, false));
    }

    private void initializeIdList(java.util.Map<String, org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __map, java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __idList) {
        __idList.add(__map.get("id"));
    }

    private void initializeList(java.util.Map<String, org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __map, java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __list) {
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
    public void preInsert(com.family.expensemanager.auth.domain.entity.RefreshToken entity, org.seasar.doma.jdbc.entity.PreInsertContext<com.family.expensemanager.auth.domain.entity.RefreshToken> context) {
        Class __listenerClass = org.seasar.doma.jdbc.entity.NullEntityListener.class;
        org.seasar.doma.jdbc.entity.NullEntityListener __listener = context.getConfig().getEntityListenerProvider().get(__listenerClass, __listenerSupplier);
        __listener.preInsert(entity, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void preUpdate(com.family.expensemanager.auth.domain.entity.RefreshToken entity, org.seasar.doma.jdbc.entity.PreUpdateContext<com.family.expensemanager.auth.domain.entity.RefreshToken> context) {
        Class __listenerClass = org.seasar.doma.jdbc.entity.NullEntityListener.class;
        org.seasar.doma.jdbc.entity.NullEntityListener __listener = context.getConfig().getEntityListenerProvider().get(__listenerClass, __listenerSupplier);
        __listener.preUpdate(entity, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void preDelete(com.family.expensemanager.auth.domain.entity.RefreshToken entity, org.seasar.doma.jdbc.entity.PreDeleteContext<com.family.expensemanager.auth.domain.entity.RefreshToken> context) {
        Class __listenerClass = org.seasar.doma.jdbc.entity.NullEntityListener.class;
        org.seasar.doma.jdbc.entity.NullEntityListener __listener = context.getConfig().getEntityListenerProvider().get(__listenerClass, __listenerSupplier);
        __listener.preDelete(entity, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void postInsert(com.family.expensemanager.auth.domain.entity.RefreshToken entity, org.seasar.doma.jdbc.entity.PostInsertContext<com.family.expensemanager.auth.domain.entity.RefreshToken> context) {
        Class __listenerClass = org.seasar.doma.jdbc.entity.NullEntityListener.class;
        org.seasar.doma.jdbc.entity.NullEntityListener __listener = context.getConfig().getEntityListenerProvider().get(__listenerClass, __listenerSupplier);
        __listener.postInsert(entity, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void postUpdate(com.family.expensemanager.auth.domain.entity.RefreshToken entity, org.seasar.doma.jdbc.entity.PostUpdateContext<com.family.expensemanager.auth.domain.entity.RefreshToken> context) {
        Class __listenerClass = org.seasar.doma.jdbc.entity.NullEntityListener.class;
        org.seasar.doma.jdbc.entity.NullEntityListener __listener = context.getConfig().getEntityListenerProvider().get(__listenerClass, __listenerSupplier);
        __listener.postUpdate(entity, context);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void postDelete(com.family.expensemanager.auth.domain.entity.RefreshToken entity, org.seasar.doma.jdbc.entity.PostDeleteContext<com.family.expensemanager.auth.domain.entity.RefreshToken> context) {
        Class __listenerClass = org.seasar.doma.jdbc.entity.NullEntityListener.class;
        org.seasar.doma.jdbc.entity.NullEntityListener __listener = context.getConfig().getEntityListenerProvider().get(__listenerClass, __listenerSupplier);
        __listener.postDelete(entity, context);
    }

    @Override
    public java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> getEntityPropertyTypes() {
        return __entityPropertyTypes;
    }

    @Override
    public org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?> getEntityPropertyType(String __name) {
        return __entityPropertyTypeMap.get(__name);
    }

    @Override
    public java.util.List<org.seasar.doma.jdbc.entity.EntityPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> getIdPropertyTypes() {
        return __idPropertyTypes;
    }

    @SuppressWarnings("unchecked")
    @Override
    public org.seasar.doma.jdbc.entity.GeneratedIdPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?, ?> getGeneratedIdPropertyType() {
        return (org.seasar.doma.jdbc.entity.GeneratedIdPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?, ?>)__entityPropertyTypeMap.get("id");
    }

    @SuppressWarnings("unchecked")
    @Override
    public org.seasar.doma.jdbc.entity.VersionPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?, ?> getVersionPropertyType() {
        return (org.seasar.doma.jdbc.entity.VersionPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?, ?>)__entityPropertyTypeMap.get("null");
    }

    @SuppressWarnings("unchecked")
    @Override
    public org.seasar.doma.jdbc.entity.TenantIdPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?, ?> getTenantIdPropertyType() {
        return (org.seasar.doma.jdbc.entity.TenantIdPropertyType<com.family.expensemanager.auth.domain.entity.RefreshToken, ?, ?>)__entityPropertyTypeMap.get("null");
    }

    @Override
    public com.family.expensemanager.auth.domain.entity.RefreshToken newEntity(java.util.Map<String, org.seasar.doma.jdbc.entity.Property<com.family.expensemanager.auth.domain.entity.RefreshToken, ?>> __args) {
        com.family.expensemanager.auth.domain.entity.RefreshToken entity = new com.family.expensemanager.auth.domain.entity.RefreshToken();
        if (__args.get("id") != null) __args.get("id").save(entity);
        if (__args.get("userId") != null) __args.get("userId").save(entity);
        if (__args.get("tokenHash") != null) __args.get("tokenHash").save(entity);
        if (__args.get("expiresAt") != null) __args.get("expiresAt").save(entity);
        if (__args.get("revoked") != null) __args.get("revoked").save(entity);
        return entity;
    }

    @Override
    public Class<com.family.expensemanager.auth.domain.entity.RefreshToken> getEntityClass() {
        return com.family.expensemanager.auth.domain.entity.RefreshToken.class;
    }

    @Override
    public com.family.expensemanager.auth.domain.entity.RefreshToken getOriginalStates(com.family.expensemanager.auth.domain.entity.RefreshToken __entity) {
        return null;
    }

    @Override
    public void saveCurrentStates(com.family.expensemanager.auth.domain.entity.RefreshToken __entity) {
    }

    /**
     * @return the singleton
     */
    public static _RefreshToken getSingletonInternal() {
        return __singleton;
    }

    /**
     * @return the new instance
     */
    public static _RefreshToken newInstance() {
        return new _RefreshToken();
    }

}
