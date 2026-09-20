package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.WalletTransfer;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Dao
public interface WalletTransferDao {

    @Insert
    int insert(WalletTransfer transfer);

    @Update
    int update(WalletTransfer transfer);

    @Delete
    int delete(WalletTransfer transfer);

    @Select
    Optional<WalletTransfer> selectById(Long id);

    @Select
    long countByFamilyId(Long familyId);

    @Select
    List<WalletTransfer> selectByFamilyIdPaged(Long familyId, int limit, int offset);

    @Select
    BigDecimal sumAmountIntoWallet(Long walletId);

    @Select
    BigDecimal sumAmountFromWallet(Long walletId);

    @Select
    long countByWalletId(Long walletId);
}
