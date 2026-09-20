package com.family.expensemanager.expense.dao;

import org.seasar.doma.Dao;
import org.seasar.doma.MapKeyNamingType;
import org.seasar.doma.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregate report queries over TRANSACTIONS. All date bounds are [fromDate, toExclusive)
 * on occurred_at; soft-deleted rows are excluded and wallet transfers are never involved.
 */
@Dao
public interface ReportDao {

    /** Rows: categoryId, type, total. */
    @Select(mapKeyNaming = MapKeyNamingType.CAMEL_CASE)
    List<Map<String, Object>> sumByCategoryAndType(Long familyId, LocalDate fromDate, LocalDate toExclusive);

    /** Rows: bucket (yyyy-MM-dd), type, total. */
    @Select(mapKeyNaming = MapKeyNamingType.CAMEL_CASE)
    List<Map<String, Object>> sumByDayAndType(Long familyId, LocalDate fromDate, LocalDate toExclusive);

    /** Rows: bucket (yyyy-MM), type, total. */
    @Select(mapKeyNaming = MapKeyNamingType.CAMEL_CASE)
    List<Map<String, Object>> sumByMonthAndType(Long familyId, LocalDate fromDate, LocalDate toExclusive);

    /** Rows: userId, type, total. */
    @Select(mapKeyNaming = MapKeyNamingType.CAMEL_CASE)
    List<Map<String, Object>> sumByUserAndType(Long familyId, LocalDate fromDate, LocalDate toExclusive);

    /** Rows: userId, displayName — the latest non-null created_by_name per user in the range. */
    @Select(mapKeyNaming = MapKeyNamingType.CAMEL_CASE)
    List<Map<String, Object>> selectLatestMemberNames(Long familyId, LocalDate fromDate, LocalDate toExclusive);
}
