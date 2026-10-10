package com.family.expensemanager.expense.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

import org.seasar.doma.jdbc.UniqueConstraintException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.family.expensemanager.common.exception.ApiException;
import com.family.expensemanager.common.exception.BadRequestException;
import com.family.expensemanager.common.exception.ConflictException;
import com.family.expensemanager.common.exception.ServiceException;
import com.family.expensemanager.expense.dao.IdempotencyKeyDao;
import com.family.expensemanager.expense.domain.entity.IdempotencyKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a POST handler's body at most once per {@code (familyId, scope, Idempotency-Key)} — see
 * {@code TransactionService}, {@code TransactionApprovalService} and {@code WalletTransferService}. A client that didn't
 * get a response (timeout, dropped connection...) can safely retry with the same key and get back the
 * exact same result instead of creating a second transaction/transfer; a blank/absent key means the
 * caller opted out and {@code action} just runs normally, no dedup.
 *
 * A reservation row is inserted (unique on family+scope+key) *before* {@code action} runs, so two
 * concurrent requests with the same key can't both slip through — the second one's insert hits the
 * unique constraint and is turned into a 409. If {@code action} throws, the reservation is removed so a
 * genuinely failed attempt (e.g. validation error) doesn't permanently lock that key out from ever being
 * retried. A reservation whose owner crashed before finishing is only held for {@link #IN_FLIGHT_TIMEOUT}
 * before being treated as abandoned and reusable.
 *
 * The key is bound to the request it was first used with (SHA-256 of its JSON): a replay must carry the same
 * body, and reusing a key for a different request is refused with 422 rather than answered with the first
 * request's result. Old keys are purged by {@code IdempotencyKeyCleanupScheduler}.
 *
 * @author boyquynhluu
 */
@Component
@RequiredArgsConstructor
@Slf4j(topic = "IdempotencyGuard")
public class IdempotencyGuard {

    private static final Duration IN_FLIGHT_TIMEOUT = Duration.ofMinutes(1);
    // = IDEMPOTENCY_KEYS.idempotency_key VARCHAR(255): longer keys would fail the insert with a 500.
    private static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyKeyDao idempotencyKeyDao;
    private final ObjectMapper objectMapper;

    /**
     * @param request the request body the key belongs to — only hashed, to tell a genuine retry (same body)
     *                from a key reused for a different request
     */
    public <T> T runOnce(Long familyId, String scope, String idempotencyKey, Object request, Class<T> responseType,
                         Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }
        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException("Idempotency-Key không được dài quá " + MAX_KEY_LENGTH + " ký tự");
        }
        String requestHash = sha256(toJson(request));

        Optional<IdempotencyKey> existing = idempotencyKeyDao.selectByFamilyIdAndScopeAndKey(familyId, scope, idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyKey record = existing.get();
            if (record.getRequestHash() != null && !record.getRequestHash().equals(requestHash)) {
                log.warn("runOnce - key reused for a different request, scope={}, key={}", scope, idempotencyKey);
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Idempotency-Key này đã được dùng cho một yêu cầu khác");
            }
            if (record.getResponseJson() != null) {
                log.info("runOnce - replaying cached response, scope={}, key={}", scope, idempotencyKey);
                return fromJson(record.getResponseJson(), responseType);
            }
            if (record.getCreatedAt().isAfter(LocalDateTime.now().minus(IN_FLIGHT_TIMEOUT))) {
                throw new ConflictException("Yêu cầu với Idempotency-Key này đang được xử lý, vui lòng thử lại sau");
            }
            log.warn("runOnce - abandoned reservation past timeout, reusing key, scope={}, key={}", scope, idempotencyKey);
            idempotencyKeyDao.delete(record);
        }

        IdempotencyKey reservation = new IdempotencyKey();
        reservation.setFamilyId(familyId);
        reservation.setScope(scope);
        reservation.setIdempotencyKey(idempotencyKey);
        reservation.setRequestHash(requestHash);
        reservation.setCreatedAt(LocalDateTime.now());
        try {
            idempotencyKeyDao.insert(reservation);
        } catch (UniqueConstraintException e) {
            // Race: another request with the same key won the insert between our SELECT and this INSERT.
            throw new ConflictException("Yêu cầu với Idempotency-Key này đang được xử lý, vui lòng thử lại sau");
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            idempotencyKeyDao.delete(reservation);
            throw e;
        }

        reservation.setResponseJson(toJson(result));
        idempotencyKeyDao.update(reservation);
        return result;
    }

    /** Deletes keys created before {@code cutoff}; returns how many. */
    @Transactional
    public int purgeCreatedBefore(LocalDateTime cutoff) {
        return idempotencyKeyDao.deleteCreatedBefore(cutoff);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw ServiceException.unexpected("IdempotencyGuard.sha256", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw ServiceException.unexpected("IdempotencyGuard.toJson", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw ServiceException.unexpected("IdempotencyGuard.fromJson", e);
        }
    }
}
