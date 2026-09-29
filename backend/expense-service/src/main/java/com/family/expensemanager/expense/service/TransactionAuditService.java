package com.family.expensemanager.expense.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.expense.dao.TransactionAuditLogDao;
import com.family.expensemanager.expense.domain.entity.TransactionAuditLog;
import com.family.expensemanager.expense.dto.TransactionAuditLogResponse;
import com.family.expensemanager.expense.dto.TransactionSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes and reads TRANSACTION_AUDIT_LOGS. {@link #record} joins the caller's transaction (default
 * propagation) on purpose: the log row commits or rolls back together with the change it describes, so the
 * history can never claim a change that didn't happen, nor miss one that did.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "TransactionAuditService")
public class TransactionAuditService {

    public static final String ACTION_CREATED = "CREATED";
    public static final String ACTION_UPDATED = "UPDATED";
    public static final String ACTION_DELETED = "DELETED";
    public static final String ACTION_RESTORED = "RESTORED";

    private static final int MAX_ACTOR_NAME_LENGTH = 100;

    private final TransactionAuditLogDao auditLogDao;
    private final ObjectMapper objectMapper;

    public void record(String action, Long familyId, Long transactionId, TransactionSnapshot before,
                       TransactionSnapshot after, Long actorUserId, String actorName) {
        TransactionAuditLog entry = new TransactionAuditLog();
        entry.setFamilyId(familyId);
        entry.setTransactionId(transactionId);
        entry.setAction(action);
        entry.setActorUserId(actorUserId);
        entry.setActorName(actorName != null && actorName.length() > MAX_ACTOR_NAME_LENGTH
                ? actorName.substring(0, MAX_ACTOR_NAME_LENGTH) : actorName);
        entry.setBeforeJson(toJson(before));
        entry.setAfterJson(toJson(after));
        entry.setCreatedAt(LocalDateTime.now());
        auditLogDao.insert(entry);
    }

    /** Empty (not 404) for a transaction with no history — e.g. one created before the audit log existed. */
    @Transactional(readOnly = true)
    public List<TransactionAuditLogResponse> listHistory(Long familyId, Long transactionId) {
        log.info("listHistory - start, familyId={}, transactionId={}", familyId, transactionId);
        return auditLogDao.selectByTransactionIdAndFamilyId(transactionId, familyId).stream()
                .map(e -> new TransactionAuditLogResponse(
                        e.getId(), e.getAction(), e.getActorUserId(), e.getActorName(),
                        fromJson(e.getBeforeJson()), fromJson(e.getAfterJson()), e.getCreatedAt()))
                .toList();
    }

    private String toJson(TransactionSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không ghi được lịch sử giao dịch", e);
        }
    }

    private TransactionSnapshot fromJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, TransactionSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không đọc được lịch sử giao dịch", e);
        }
    }
}
