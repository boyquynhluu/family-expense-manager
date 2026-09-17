package com.family.expensemanager.expense.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads/writes receipt photos on local disk under {@code receipt.storage-path}
 * (backed by the {@code receipt-uploads} Docker volume — see infra/docker-compose.yml
 * — so files survive container recreation). Only the relative path this returns is
 * ever stored in the DB; {@link TransactionService} owns validation and DB updates.
 */
@Component
@Slf4j(topic = "ReceiptStorageService")
public class ReceiptStorageService {

    private final Path basePath;

    public ReceiptStorageService(@Value("${receipt.storage-path:/data/receipts}") String basePath) {
        this.basePath = Path.of(basePath);
    }

    /** Returns the relative path (family-scoped subfolder) to store as {@code receipt_path}. */
    public String save(Long familyId, Long transactionId, MultipartFile file) throws IOException {
        Path dir = basePath.resolve(String.valueOf(familyId));
        Files.createDirectories(dir);
        String filename = transactionId + "-" + System.currentTimeMillis() + extensionOf(file);
        Path target = dir.resolve(filename);
        file.transferTo(target);
        return familyId + "/" + filename;
    }

    public byte[] read(String relativePath) throws IOException {
        return Files.readAllBytes(resolveWithinBase(relativePath));
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolveWithinBase(relativePath));
        } catch (IOException e) {
            log.warn("Không xoá được file hoá đơn {}", relativePath, e);
        }
    }

    /** Guards against a relative path escaping {@code basePath} (e.g. via "../"). */
    private Path resolveWithinBase(String relativePath) {
        Path resolved = basePath.resolve(relativePath).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("Đường dẫn không hợp lệ: " + relativePath);
        }
        return resolved;
    }

    private String extensionOf(MultipartFile file) {
        String original = file.getOriginalFilename();
        int dot = original == null ? -1 : original.lastIndexOf('.');
        return dot >= 0 ? original.substring(dot) : "";
    }
}
