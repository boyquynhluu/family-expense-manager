package com.family.expensemanager.expense.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import org.seasar.doma.jdbc.UniqueConstraintException;
import org.springframework.stereotype.Component;

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
 * {@code TransactionController}/{@code WalletTransferController}, the two callers. A client that didn't
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
 * @author boyquynhluu
 */
@Component
@RequiredArgsConstructor
@Slf4j(topic = "IdempotencyGuard")
public class IdempotencyGuard {

    private static final Duration IN_FLIGHT_TIMEOUT = Duration.ofMinutes(1);

    private final IdempotencyKeyDao idempotencyKeyDao;
    private final ObjectMapper objectMapper;

    public <T> T runOnce(Long familyId, String scope, String idempotencyKey, Class<T> responseType, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        Optional<IdempotencyKey> existing = idempotencyKeyDao.selectByFamilyIdAndScopeAndKey(familyId, scope, idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyKey record = existing.get();
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
