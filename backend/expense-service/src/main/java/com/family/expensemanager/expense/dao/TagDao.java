package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Tag;
import com.family.expensemanager.expense.domain.entity.TransactionTag;
import org.seasar.doma.BatchInsert;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.MapKeyNamingType;
import org.seasar.doma.Select;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * README C6: tags and their links to transactions.
 *
 * @author boyquynhluu
 */
@Dao
public interface TagDao {

    @Insert
    int insert(Tag tag);

    @Select
    Optional<Tag> selectByFamilyAndName(Long familyId, String name);

    /** Alphabetical. */
    @Select
    List<Tag> selectByFamilyId(Long familyId);

    @BatchInsert
    int[] insertLinks(List<TransactionTag> links);

    @Delete(sqlFile = true)
    int deleteLinksByTransactionId(Long transactionId);

    /** Rows: transactionId, name — the tags of each given transaction. */
    @Select(mapKeyNaming = MapKeyNamingType.CAMEL_CASE)
    List<Map<String, Object>> selectNamesByTransactionIds(List<Long> transactionIds);

    @Select
    List<Long> selectTransactionIdsByTagId(Long tagId);
}
