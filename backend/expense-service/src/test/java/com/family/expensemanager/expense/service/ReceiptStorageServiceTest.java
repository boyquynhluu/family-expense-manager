package com.family.expensemanager.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ReceiptStorageServiceTest {

    @TempDir
    Path tempDir;

    private ReceiptStorageService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptStorageService(tempDir.toString());
    }

    @Test
    void save_choosesExtensionFromTheValidatedType_notTheClientFilename() throws IOException {
        // A client claiming .html for real JPEG bytes must not end up saved as .html: the on-disk
        // extension always comes from the already-sniffed, validated type (see TransactionService).
        MockMultipartFile file = new MockMultipartFile("file", "hoadon.html", "text/html", new byte[] {1, 2, 3});

        String relativePath = service.save(7L, 42L, file, "image/jpeg");

        assertThat(relativePath).startsWith("7/42-").endsWith(".jpg");
        assertThat(Files.exists(tempDir.resolve(relativePath))).isTrue();
    }

    @Test
    void save_mapsEveryAllowedTypeToItsOwnExtension() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "x", "x", new byte[] {1});

        assertThat(service.save(1L, 1L, file, "image/jpeg")).endsWith(".jpg");
        assertThat(service.save(1L, 2L, file, "image/png")).endsWith(".png");
        assertThat(service.save(1L, 3L, file, "image/webp")).endsWith(".webp");
    }

    @Test
    void save_rejectsAnUnvalidatedType() {
        MockMultipartFile file = new MockMultipartFile("file", "x", "x", new byte[] {1});

        assertThatThrownBy(() -> service.save(1L, 1L, file, "image/svg+xml"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readAndDelete_roundTrip() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "hoadon.jpg", "image/jpeg", new byte[] {9, 8, 7});
        String relativePath = service.save(7L, 42L, file, "image/jpeg");

        assertThat(service.read(relativePath)).isEqualTo(new byte[] {9, 8, 7});

        service.delete(relativePath);
        assertThat(Files.exists(tempDir.resolve(relativePath))).isFalse();
    }

    @Test
    void delete_isANoOp_whenFileDoesNotExist() {
        service.delete("7/does-not-exist.jpg");
        // No exception — see ReceiptStorageService#delete's own log-and-continue behaviour.
    }

    @Test
    void read_rejectsAPathThatEscapesTheBaseDirectory() {
        assertThatThrownBy(() -> service.read("../outside.jpg")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_rejectsAPathThatEscapesTheBaseDirectory_ratherThanDeletingOutsideFiles() throws IOException {
        Path outside = Files.createTempFile(tempDir.getParent(), "escape", ".jpg");
        String traversal = "../" + outside.getFileName();

        assertThatThrownBy(() -> service.delete(traversal)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.exists(outside)).isTrue();
        Files.deleteIfExists(outside);
    }
}
