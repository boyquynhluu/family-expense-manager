/**
 * Doma DAOs used by the batch jobs (same layout as the services: {@code @Dao} interface here, SQL in
 * src/main/resources/META-INF/com/family/expensemanager/batch/dao/&lt;DaoName&gt;/&lt;method&gt;.sql, entities in
 * a domain.entity package). Needs a Doma {@code Config} bean (see a service's DomaConfiguration) before the first
 * DAO — README "Batch" → "Đọc/ghi dữ liệu nghiệp vụ".
 */
package com.family.expensemanager.batch.dao;
