package com.family.expensemanager.expense.service;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.common.dto.PageResponse;
import com.family.expensemanager.common.event.ExpenseEvent;
import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.NotFoundException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.TransferRequestDao;
import com.family.expensemanager.expense.dao.WalletDao;
import com.family.expensemanager.expense.domain.TransactionAmounts;
import com.family.expensemanager.expense.domain.entity.TransferRequest;
import com.family.expensemanager.expense.domain.entity.Wallet;
import com.family.expensemanager.expense.dto.CreateTransferRequestRequest;
import com.family.expensemanager.expense.dto.CreateWalletTransferRequest;
import com.family.expensemanager.expense.dto.TransferRequestResponse;
import com.family.expensemanager.expense.dto.TrashCountResponse;
import com.family.expensemanager.expense.dto.WalletTransferResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Yêu cầu chuyển tiền": member B asks the owner of wallet A (another member's private wallet) to move money
 * into B's own private wallet. The owner gets an email; the request stays PENDING ("Đang chờ") until the owner
 * approves it — which makes the real transfer through {@link WalletTransferService}, with all of its checks
 * (balance, 10.000đ–5.000.000đ, currency) — and it becomes COMPLETED ("Đã chuyển"), or rejects it (REJECTED, "Từ
 * chối"). Nobody but the wallet's owner can ever move money out of it.
 *
 * @author boyquynhluu
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "TransferRequestService")
public class TransferRequestService {

    /** Open requests one member may have at a time, so nobody can flood an owner's inbox. */
    static final int MAX_PENDING_PER_REQUESTER = 5;

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 100;

    private final TransferRequestDao transferRequestDao;
    private final WalletDao walletDao;
    private final WalletService walletService;
    private final WalletTransferService walletTransferService;
    private final ApplicationEventPublisher eventPublisher;

    public TransferRequestResponse create(Long familyId, Long requesterUserId, String requesterName,
                                          CreateTransferRequestRequest request) {
        try {
            log.info("create - start, familyId={}, requester={}, from={}, to={}",
                    familyId, requesterUserId, request.fromWalletId(), request.toWalletId());
            if (request.fromWalletId().equals(request.toWalletId())) {
                throw logged(log, new BadRequestException("Ví nguồn và ví nhận phải khác nhau"));
            }
            TransactionAmounts.problem(request.amount()).ifPresent(message -> {
                throw logged(log, new BadRequestException(message));
            });
            Wallet from = walletService.requireOwnedByFamily(request.fromWalletId(), familyId);
            if (from.getOwnerUserId() == null) {
                throw logged(log, new BadRequestException("Không gửi yêu cầu cho ví chung \"" + from.getName()
                        + "\" — chỉ xin được từ ví riêng của thành viên khác"));
            }
            if (from.getOwnerUserId().equals(requesterUserId)) {
                throw logged(log, new BadRequestException("Ví \"" + from.getName()
                        + "\" là ví của bạn — hãy dùng Chuyển tiền thay vì gửi yêu cầu"));
            }
            Wallet to = walletService.requireOwnedByFamily(request.toWalletId(), familyId);
            if (!requesterUserId.equals(to.getOwnerUserId())) {
                throw logged(log, new ApiException(HttpStatus.FORBIDDEN,
                        "Ví nhận \"" + to.getName() + "\" phải là ví riêng của bạn"));
            }
            if (!from.getCurrency().equals(to.getCurrency())) {
                throw logged(log, new BadRequestException("Hai ví phải cùng loại tiền tệ"));
            }
            if (transferRequestDao.countPendingByRequester(familyId, requesterUserId) >= MAX_PENDING_PER_REQUESTER) {
                throw logged(log, new BadRequestException("Bạn đang có " + MAX_PENDING_PER_REQUESTER
                        + " yêu cầu chuyển tiền đang chờ — hãy đợi được xử lý trước khi gửi thêm"));
            }

            TransferRequest r = new TransferRequest();
            r.setFamilyId(familyId);
            r.setRequesterUserId(requesterUserId);
            r.setRequesterName(truncate(requesterName));
            r.setApproverUserId(from.getOwnerUserId());
            r.setFromWalletId(from.getId());
            r.setToWalletId(to.getId());
            r.setAmount(request.amount());
            r.setNote(request.note());
            r.setStatus(TransferRequest.PENDING);
            r.setCreatedAt(LocalDateTime.now());
            transferRequestDao.insert(r);

            publish(ExpenseEvent.TRANSFER_REQUESTED, r, requesterUserId, requesterName, r.getApproverUserId(),
                    from, to);
            return TransferRequestResponse.from(r, from.getName(), to.getName(), requesterUserId);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransferRequestService.create", e);
        }
    }

    public PageResponse<TransferRequestResponse> listForUser(Long familyId, Long userId, int page, int size) {
        try {
            log.info("listForUser - start, familyId={}, userId={}, page={}, size={}", familyId, userId, page, size);
            if (page < 0) {
                throw logged(log, new BadRequestException("page phải >= 0"));
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw logged(log, new BadRequestException("size phải trong khoảng 1-" + MAX_PAGE_SIZE));
            }
            long total = transferRequestDao.countVisibleToUser(familyId, userId);
            Map<Long, String> names = walletDao.selectByFamilyId(familyId).stream()
                    .collect(Collectors.toMap(Wallet::getId, Wallet::getName));
            List<TransferRequestResponse> content =
                    transferRequestDao.selectVisibleToUserPaged(familyId, userId, size, page * size).stream()
                            .map(r -> TransferRequestResponse.from(r, names.get(r.getFromWalletId()),
                                    names.get(r.getToWalletId()), userId))
                            .toList();
            return PageResponse.of(content, page, size, total);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransferRequestService.listForUser", e);
        }
    }

    /** How many requests are waiting for this user to decide — the badge on the Wallets page. */
    @Transactional(readOnly = true)
    public TrashCountResponse countPendingForApprover(Long familyId, Long userId) {
        return new TrashCountResponse(transferRequestDao.countPendingForApprover(familyId, userId));
    }

    /**
     * Makes the transfer as the wallet's owner and marks the request COMPLETED, in one DB transaction: if the
     * transfer is refused (e.g. not enough money any more) the request stays PENDING, and if someone decided it
     * meanwhile (double click) the transfer is rolled back.
     */
    public TransferRequestResponse approve(Long familyId, Long requestId, Long callerUserId, String callerEmail,
                                           String callerName, String callerRole) {
        try {
            log.info("approve - start, familyId={}, requestId={}", familyId, requestId);
            TransferRequest r = requirePendingForCaller(familyId, requestId, callerUserId);
            Wallet from = walletService.requireOwnedByFamily(r.getFromWalletId(), familyId);
            if (!callerUserId.equals(from.getOwnerUserId())) {
                throw logged(log, new ApiException(HttpStatus.FORBIDDEN,
                        "Ví \"" + from.getName() + "\" không còn là ví của bạn — không thể duyệt yêu cầu này"));
            }
            Wallet to = walletService.requireOwnedByFamily(r.getToWalletId(), familyId);

            WalletTransferResponse transfer = walletTransferService.create(familyId, callerUserId, callerEmail,
                    callerName, callerRole,
                    new CreateWalletTransferRequest(from.getId(), to.getId(), r.getAmount(), LocalDateTime.now(),
                            transferNote(r)),
                    null);

            LocalDateTime now = LocalDateTime.now();
            if (transferRequestDao.decide(r.getId(), TransferRequest.COMPLETED, callerUserId, truncate(callerName),
                    now, transfer.id()) == 0) {
                throw logged(log, new ConflictException("Yêu cầu này vừa được xử lý — vui lòng tải lại trang"));
            }
            r.setStatus(TransferRequest.COMPLETED);
            r.setDecidedByUserId(callerUserId);
            r.setDecidedByName(truncate(callerName));
            r.setDecidedAt(now);
            r.setTransferId(transfer.id());

            publish(ExpenseEvent.TRANSFER_REQUEST_APPROVED, r, callerUserId, callerName, r.getRequesterUserId(),
                    from, to);
            return TransferRequestResponse.from(r, from.getName(), to.getName(), callerUserId);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransferRequestService.approve", e);
        }
    }

    public TransferRequestResponse reject(Long familyId, Long requestId, Long callerUserId, String callerName) {
        try {
            log.info("reject - start, familyId={}, requestId={}", familyId, requestId);
            TransferRequest r = requirePendingForCaller(familyId, requestId, callerUserId);
            LocalDateTime now = LocalDateTime.now();
            if (transferRequestDao.decide(r.getId(), TransferRequest.REJECTED, callerUserId, truncate(callerName),
                    now, null) == 0) {
                throw logged(log, new ConflictException("Yêu cầu này vừa được xử lý — vui lòng tải lại trang"));
            }
            r.setStatus(TransferRequest.REJECTED);
            r.setDecidedByUserId(callerUserId);
            r.setDecidedByName(truncate(callerName));
            r.setDecidedAt(now);

            Map<Long, Wallet> wallets = walletDao.selectByFamilyId(familyId).stream()
                    .collect(Collectors.toMap(Wallet::getId, Function.identity()));
            Wallet from = wallets.get(r.getFromWalletId());
            Wallet to = wallets.get(r.getToWalletId());
            publish(ExpenseEvent.TRANSFER_REQUEST_REJECTED, r, callerUserId, callerName, r.getRequesterUserId(),
                    from, to);
            return TransferRequestResponse.from(r, from == null ? null : from.getName(),
                    to == null ? null : to.getName(), callerUserId);
        } catch (ApiException | AccessDeniedException | AuthenticationException | UncheckedIOException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.unexpected("TransferRequestService.reject", e);
        }
    }

    /** Only the wallet owner it was sent to may decide; anyone else gets 404 (no hint it even exists). */
    private TransferRequest requirePendingForCaller(Long familyId, Long requestId, Long callerUserId) {
        TransferRequest r = transferRequestDao.selectById(requestId)
                .filter(x -> x.getFamilyId().equals(familyId))
                .filter(x -> x.getApproverUserId().equals(callerUserId) || x.getRequesterUserId().equals(callerUserId))
                .orElseThrow(() -> logged(log, new NotFoundException("Yêu cầu chuyển tiền không tồn tại: " + requestId)));
        if (!r.getApproverUserId().equals(callerUserId)) {
            throw logged(log, new ApiException(HttpStatus.FORBIDDEN,
                    "Chỉ chủ ví được yêu cầu mới có thể đồng ý hoặc từ chối"));
        }
        if (!TransferRequest.PENDING.equals(r.getStatus())) {
            throw logged(log, new ConflictException("Yêu cầu này đã được xử lý"));
        }
        return r;
    }

    private void publish(String eventType, TransferRequest r, Long actorUserId, String actorName, Long targetUserId,
                         Wallet from, Wallet to) {
        eventPublisher.publishEvent(new ExpenseEvent(
                eventType, r.getFamilyId(), actorUserId, null, null, r.getAmount(), null, null, null, null,
                null, actorName, Instant.now(), null, r.getNote(),
                from == null ? null : from.getName(), to == null ? null : to.getName(), null, null, targetUserId));
    }

    private static String transferNote(TransferRequest r) {
        String base = "Theo yêu cầu của " + (r.getRequesterName() != null ? r.getRequesterName() : "thành viên");
        String note = r.getNote() == null || r.getNote().isBlank() ? base : base + ": " + r.getNote().trim();
        return note.length() > 255 ? note.substring(0, 255) : note;
    }

    private static String truncate(String name) {
        return name != null && name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
