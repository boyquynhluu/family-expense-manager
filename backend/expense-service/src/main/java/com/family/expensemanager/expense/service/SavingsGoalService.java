package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.currency.CurrencyUtil;
import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.SavingsGoalDao;
import com.family.expensemanager.expense.domain.entity.SavingsGoal;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.SavingsGoalRequest;
import com.family.expensemanager.expense.dto.SavingsGoalResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * README C1 "Mục tiêu tiết kiệm": a target amount, an optional deadline and the wallet the money is kept in;
 * progress is that wallet's balance. {@link #checkMilestones} (daily, see ReminderService) announces 50/80/100%
 * once each. Anyone may create a goal; its creator or the OWNER may change or delete it.
 *
 * @author boyquynhluu
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "SavingsGoalService")
public class SavingsGoalService {

    private static final int[] MILESTONES = {50, 80, 100};
    private static final int MAX_NAME_LENGTH = 100;

    private final SavingsGoalDao savingsGoalDao;
    private final WalletService walletService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public List<SavingsGoalResponse> list(Long familyId) {
        try {
            log.info("list - start, familyId={}", familyId);
            return savingsGoalDao.selectByFamilyId(familyId).stream().map(this::toResponse).toList();
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("SavingsGoalService.list", e);
        }
    }

    public SavingsGoalResponse create(Long familyId, Long userId, String userName, SavingsGoalRequest request) {
        try {
            log.info("create - start, familyId={}", familyId);
            walletService.requireOwnedByFamily(request.walletId(), familyId);
            SavingsGoal goal = new SavingsGoal();
            goal.setFamilyId(familyId);
            apply(goal, request);
            goal.setCreatedByUserId(userId);
            goal.setCreatedByName(userName != null && userName.length() > MAX_NAME_LENGTH
                    ? userName.substring(0, MAX_NAME_LENGTH) : userName);
            goal.setCreatedAt(LocalDateTime.now(clock));
            // Already-reached milestones are not "news": start from where the wallet already is.
            goal.setMilestoneReached(reachedMilestone(percentOf(goal)));
            savingsGoalDao.insert(goal);
            return toResponse(goal);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("SavingsGoalService.create", e);
        }
    }

    public SavingsGoalResponse update(Long familyId, Long goalId, Long userId, boolean callerIsOwner,
                                      SavingsGoalRequest request) {
        try {
            log.info("update - start, familyId={}, goalId={}", familyId, goalId);
            SavingsGoal goal = requireModifiable(familyId, goalId, userId, callerIsOwner);
            walletService.requireOwnedByFamily(request.walletId(), familyId);
            boolean retargeted = goal.getTargetAmount().compareTo(request.targetAmount()) != 0
                    || !goal.getWalletId().equals(request.walletId());
            apply(goal, request);
            if (retargeted) {
                goal.setMilestoneReached(reachedMilestone(percentOf(goal)));
            }
            savingsGoalDao.update(goal);
            return toResponse(goal);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("SavingsGoalService.update", e);
        }
    }

    public void delete(Long familyId, Long goalId, Long userId, boolean callerIsOwner) {
        try {
            log.info("delete - start, familyId={}, goalId={}", familyId, goalId);
            savingsGoalDao.delete(requireModifiable(familyId, goalId, userId, callerIsOwner));
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("SavingsGoalService.delete", e);
        }
    }

    /** Daily: announces each newly reached 50/80/100% milestone once (in app + email to the family). */
    public void checkMilestones() {
        for (SavingsGoal goal : savingsGoalDao.selectInProgress()) {
            try {
                int reached = reachedMilestone(percentOf(goal));
                if (reached <= goal.getMilestoneReached()) {
                    continue;
                }
                goal.setMilestoneReached(reached);
                savingsGoalDao.update(goal);
                BigDecimal saved = savedOf(goal);
                eventPublisher.publishEvent(ExpenseEvent.notice(ExpenseEvent.SAVINGS_MILESTONE, goal.getFamilyId(),
                        goal.getCreatedByUserId(), goal.getCreatedByName(), null, null,
                        reached >= 100 ? "Đã đạt mục tiêu tiết kiệm!" : "Mục tiêu tiết kiệm đạt " + reached + "%",
                        "Mục tiêu \"" + goal.getName() + "\": đã có " + CurrencyUtil.formatCurrency(saved) + " / "
                                + CurrencyUtil.formatCurrency(goal.getTargetAmount()) + " (" + reached + "%).",
                        "/goals"));
            } catch (Exception e) {
                log.warn("Không kiểm tra được mốc tiết kiệm goalId={}", goal.getId(), e);
            }
        }
    }

    private void apply(SavingsGoal goal, SavingsGoalRequest request) {
        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        goal.setDeadline(request.deadline());
        goal.setWalletId(request.walletId());
        goal.setArchived(Boolean.TRUE.equals(request.archived()));
    }

    private SavingsGoal requireModifiable(Long familyId, Long goalId, Long userId, boolean callerIsOwner) {
        SavingsGoal goal = savingsGoalDao.selectById(goalId)
                .filter(g -> g.getFamilyId().equals(familyId))
                .orElseThrow(() -> logged(log, new NotFoundException("Mục tiêu không tồn tại: " + goalId)));
        if (!callerIsOwner && !Objects.equals(goal.getCreatedByUserId(), userId)) {
            throw logged(log, new ApiException(HttpStatus.FORBIDDEN, "Chỉ người tạo hoặc chủ hộ mới được sửa mục tiêu này"));
        }
        return goal;
    }

    private SavingsGoalResponse toResponse(SavingsGoal goal) {
        return new SavingsGoalResponse(goal.getId(), goal.getName(), goal.getTargetAmount(), goal.getDeadline(),
                goal.getWalletId(), savedOf(goal), percentOf(goal), goal.getArchived(), goal.getCreatedByUserId(),
                goal.getCreatedByName());
    }

    private BigDecimal savedOf(SavingsGoal goal) {
        try {
            Wallet wallet = walletService.requireOwnedByFamily(goal.getWalletId(), goal.getFamilyId());
            return walletService.currentBalanceOf(wallet).max(BigDecimal.ZERO);
        } catch (NotFoundException e) {
            return BigDecimal.ZERO;
        }
    }

    private int percentOf(SavingsGoal goal) {
        BigDecimal saved = savedOf(goal);
        int percent = saved.multiply(BigDecimal.valueOf(100))
                .divide(goal.getTargetAmount(), 0, RoundingMode.DOWN).intValue();
        return Math.min(percent, 100);
    }

    private static int reachedMilestone(int percent) {
        int reached = 0;
        for (int m : MILESTONES) {
            if (percent >= m) {
                reached = m;
            }
        }
        return reached;
    }
}
