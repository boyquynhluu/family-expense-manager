package com.family.expensemanager.expense.dao;

import com.family.expensemanager.expense.domain.entity.LoanPayment;
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
public interface LoanPaymentDao {

    @Insert
    int insert(LoanPayment payment);

    @Delete
    int delete(LoanPayment payment);

    @Select
    Optional<LoanPayment> selectById(Long id);

    /** Oldest first. */
    @Select
    List<LoanPayment> selectByLoanId(Long loanId);

    @Select
    BigDecimal sumByLoanId(Long loanId);
}
