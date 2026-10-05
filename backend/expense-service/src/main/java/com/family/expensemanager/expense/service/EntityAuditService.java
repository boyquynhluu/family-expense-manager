package com.family.expensemanager.expense.service;

import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.security.CurrentUser;
import com.family.expensemanager.expense.dao.EntityAuditLogDao;
import com.family.expensemanager.expense.domain.entity.EntityAuditLog;
import com.family.expensemanager.expense.dto.EntityAuditLogResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.family.expensemanager.common.exception.ExceptionLogger.logged;

/**
 * History of wallets, budgets and transfers (README A3) — the counterpart of {@link TransactionAuditService}.
 * The actor is read from the request's JWT, so services keep their signatures; outside a request (a scheduler)
 * the entry is filed as "Hệ thống".
 *
 * @author boyquynhluu
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j(topic = "EntityAuditService")
public class EntityAuditService {

    public static final String WALLET = "WALLET";
    public static final String BUDGET = "BUDGET";
    public static final String TRANSFER = "TRANSFER";
    public static final String ACTION_CREATED = "CREATED";
    public static final String ACTION_UPDATED = "UPDATED";
    public static final String ACTION_DELETED = "DELETED";
    public static final String ACTION_RESTORED = "RESTORED";

    private static final Set<String> TYPES = Set.of(WALLET, BUDGET, TRANSFER);
    private static final int MAX_ACTOR_NAME_LENGTH = 100;
    private static final String SYSTEM_ACTOR = "Hệ thống";

    private final EntityAuditLogDao auditLogDao;
    private final ObjectMapper objectMapper;

    /** {@code before}/{@code after}: any JSON-serialisable snapshot (a response record), null where it doesn't apply. */
    public void record(Long familyId, String entityType, Long entityId, String action, Object before, Object after) {
        EntityAuditLog entry = new EntityAuditLog();
        entry.setFamilyId(familyId);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        Long actorId = null;
        String actorName = SYSTEM_ACTOR;
        try {
            actorId = CurrentUser.userId();
            actorName = CurrentUser.displayName();
        } catch (RuntimeException e) {
            // No request (scheduler): keep "Hệ thống".
        }
        entry.setActorUserId(actorId);
        entry.setActorName(actorName != null && actorName.length() > MAX_ACTOR_NAME_LENGTH
                ? actorName.substring(0, MAX_ACTOR_NAME_LENGTH) : actorName);
        entry.setBeforeJson(toJson(before));
        entry.setAfterJson(toJson(after));
        entry.setCreatedAt(LocalDateTime.now());
        auditLogDao.insert(entry);
    }

    @Transactional(readOnly = true)
    public List<EntityAuditLogResponse> history(Long familyId, String entityType, Long entityId) {
        log.info("history - start, familyId={}, entityType={}, entityId={}", familyId, entityType, entityId);
        if (!TYPES.contains(entityType)) {
            throw logged(log, new BadRequestException("Loại đối tượng không hợp lệ: " + entityType));
        }
        return auditLogDao.selectByEntity(familyId, entityType, entityId).stream()
                .map(e -> new EntityAuditLogResponse(e.getId(), e.getEntityType(), e.getEntityId(), e.getAction(),
                        e.getActorUserId(), e.getActorName(), fromJson(e.getBeforeJson()), fromJson(e.getAfterJson()),
                        e.getCreatedAt()))
                .toList();
    }

    private String toJson(Object snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không ghi được lịch sử thay đổi", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không đọc được lịch sử thay đổi", e);
        }
    }
}
