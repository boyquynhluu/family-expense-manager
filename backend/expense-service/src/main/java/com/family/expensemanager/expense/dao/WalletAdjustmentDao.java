package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.WalletAdjustment;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * @author boyquynhluu
 */
@Dao
public interface WalletAdjustmentDao {

    @Insert
    int insert(WalletAdjustment adjustment);

    @Delete
    int delete(WalletAdjustment adjustment);

    @Select
    Optional<WalletAdjustment> selectById(Long id);

    /** {@code walletId} null = every wallet of the family. */
    @Select
    long countByFamilyId(Long familyId, Long walletId);

    /** Newest first; {@code walletId} null = every wallet of the family. */
    @Select
    List<WalletAdjustment> selectByFamilyIdPaged(Long familyId, Long walletId, int limit, int offset);

    /** Signed total of every adjustment of the wallet — part of its current balance. */
    @Select
    BigDecimal sumAmountByWalletId(Long walletId);

    @Select
    long countByWalletId(Long walletId);
}
