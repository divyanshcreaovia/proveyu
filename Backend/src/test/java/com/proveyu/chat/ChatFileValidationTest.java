package com.proveyu.chat;

import com.proveyu.shared.error.DomainException;
import com.proveyu.shared.storage.FileStorageService;
import com.proveyu.shared.storage.StoredChatFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatFileValidationTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;
    private Path chatDir;

    @BeforeEach
    void setUp() throws IOException {
        Path uploadDir = tempDir.resolve("resumes");
        chatDir = tempDir.resolve("chat");
        Files.createDirectories(uploadDir);
        Files.createDirectories(chatDir);

        fileStorageService = new FileStorageService(
                uploadDir.toString(),
                chatDir.toString(),
                2 * 1024 * 1024L, // 2MB max for test
                ".pdf,.doc,.docx,.jpg,.jpeg,.png"
        );
    }

    @Test
    @DisplayName("File Validation: Rejects empty file")
    void testEmptyFileValidation() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> fileStorageService.storeChatFile(emptyFile, UUID.randomUUID()))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "EMPTY_FILE");
    }

    @Test
    @DisplayName("File Validation: Rejects file exceeding maximum allowed size")
    void testFileTooLargeValidation() {
        byte[] largeBytes = new byte[3 * 1024 * 1024]; // 3MB > 2MB limit
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.pdf", "application/pdf", largeBytes);

        assertThatThrownBy(() -> fileStorageService.storeChatFile(largeFile, UUID.randomUUID()))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "FILE_TOO_LARGE");
    }

    @Test
    @DisplayName("File Validation: Rejects disallowed file extensions (.exe, .sh, .bat)")
    void testDisallowedExtensionValidation() {
        MockMultipartFile exeFile = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "dummy".getBytes());

        assertThatThrownBy(() -> fileStorageService.storeChatFile(exeFile, UUID.randomUUID()))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_FILE_TYPE");
    }

    @Test
    @DisplayName("File Validation: Rejects path traversal filename sequence")
    void testPathTraversalFilenameRejected() {
        MockMultipartFile traversalFile = new MockMultipartFile("file", "../../../etc/passwd.pdf", "application/pdf", "dummy".getBytes());

        assertThatThrownBy(() -> fileStorageService.storeChatFile(traversalFile, UUID.randomUUID()))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_PATH_SEQUENCE");
    }

    @Test
    @DisplayName("File Validation: Successfully stores valid file and generates safe unique key")
    void testValidFileSuccess() throws IOException {
        UUID senderId = UUID.randomUUID();
        MockMultipartFile validPdf = new MockMultipartFile("file", "my_resume.pdf", "application/pdf", "PDF test content".getBytes());

        StoredChatFile storedFile = fileStorageService.storeChatFile(validPdf, senderId);

        assertThat(storedFile).isNotNull();
        assertThat(storedFile.originalFileName()).isEqualTo("my_resume.pdf");
        assertThat(storedFile.storageFileName()).startsWith("chat_" + senderId);
        assertThat(storedFile.storageFileName()).endsWith(".pdf");
        assertThat(storedFile.fileSize()).isEqualTo(16L);

        // Verify physical file was written and can be loaded
        Resource resource = fileStorageService.loadChatFileAsResource(storedFile.storageFileName());
        assertThat(resource.exists()).isTrue();
        assertThat(resource.contentLength()).isEqualTo(16L);
    }

    @Test
    @DisplayName("File Validation: Load file rejects path traversal strings")
    void testLoadFileRejectsPathTraversal() {
        assertThatThrownBy(() -> fileStorageService.loadChatFileAsResource("../../../secrets.txt"))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_FILE_NAME");
    }
}
