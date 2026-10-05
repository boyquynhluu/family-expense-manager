package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.Loan;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * @author boyquynhluu
 */
@Dao
public interface LoanDao {

    @Insert
    int insert(Loan loan);

    @Update
    int update(Loan loan);

    @Delete
    int delete(Loan loan);

    @Select
    Optional<Loan> selectById(Long id);

    /** {@code status} / {@code memberUserId} null = no filter. */
    @Select
    long countByFamily(Long familyId, Long memberUserId, String status);

    /** Open loans first, soonest due date first. */
    @Select
    List<Loan> selectByFamilyPaged(Long familyId, Long memberUserId, String status, int limit, int offset);

    /**
     * What loans did to the wallet's balance, signed: +principal borrowed into it, -principal lent out of it,
     * -repayments of borrowed money out of it, +repayments of lent money into it.
     */
    @Select
    BigDecimal sumNetFlowForWallet(Long walletId);

    @Select
    long countByWalletId(Long walletId);

    /** Open loans whose due date is within [today, until] and that were not reminded for that date yet. */
    @Select
    List<Loan> selectDueForReminder(LocalDate today, LocalDate until);
}
