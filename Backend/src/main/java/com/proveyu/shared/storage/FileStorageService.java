package com.proveyu.shared.storage;

import com.proveyu.shared.error.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileStorageService {

    private final Path fileStorageLocation;
    private final Path admitCardStorageLocation;
    private final Path chatStorageLocation;
    private final long chatMaxFileSizeBytes;
    private final String chatAllowedExtensions;
    private final Set<String> allowedExtensionsSet;

    public FileStorageService(
            @Value("${file.upload-dir:uploads/resumes}") String uploadDir,
            @Value("${chat.file.upload-dir:uploads/chat}") String chatUploadDir,
            @Value("${chat.file.max-file-size-bytes:10485760}") long chatMaxFileSizeBytes,
            @Value("${chat.file.allowed-extensions:.pdf,.doc,.docx,.jpg,.jpeg,.png}") String chatAllowedExtensions) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.admitCardStorageLocation = Paths.get("uploads/admit_cards").toAbsolutePath().normalize();
        this.chatStorageLocation = Paths.get(chatUploadDir).toAbsolutePath().normalize();
        this.chatMaxFileSizeBytes = chatMaxFileSizeBytes;
        this.chatAllowedExtensions = chatAllowedExtensions;
        this.allowedExtensionsSet = Arrays.stream(chatAllowedExtensions.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        try {
            Files.createDirectories(this.fileStorageLocation);
            Files.createDirectories(this.admitCardStorageLocation);
            Files.createDirectories(this.chatStorageLocation);
            log.info("[FILE STORAGE] Initialized upload directories: resumes=[{}], admitCards=[{}], chat=[{}]",
                    this.fileStorageLocation, this.admitCardStorageLocation, this.chatStorageLocation);
        } catch (Exception ex) {
            log.error("[FILE STORAGE ERROR] Could not create storage directories: {}", ex.getMessage());
            throw new DomainException("Could not create file upload storage directories", HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_INIT_ERROR");
        }
    }

    public String storeFile(MultipartFile file, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("Uploaded file is empty", HttpStatus.BAD_REQUEST, "EMPTY_FILE");
        }

        // Validate max 5MB size
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new DomainException("File size exceeds maximum allowed limit of 5MB", HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE");
        }

        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String fileExtension = "";

        int i = originalFilename.lastIndexOf('.');
        if (i > 0) {
            fileExtension = originalFilename.substring(i).toLowerCase();
        }

        // Validate file type (PDF, DOC, DOCX)
        if (!fileExtension.equals(".pdf") && !fileExtension.equals(".doc") && !fileExtension.equals(".docx")) {
            throw new DomainException("Only PDF, DOC, and DOCX document formats are allowed", HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE");
        }

        String uniqueFileName = "resume_" + userId.toString() + "_" + System.currentTimeMillis() + fileExtension;

        try {
            if (uniqueFileName.contains("..")) {
                throw new DomainException("Filename contains invalid path sequence", HttpStatus.BAD_REQUEST, "INVALID_PATH_SEQUENCE");
            }

            Path targetLocation = this.fileStorageLocation.resolve(uniqueFileName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("[FILE STORAGE SUCCESS] File saved: path=[{}] size=[{} bytes]", targetLocation, file.getSize());
            return uniqueFileName;
        } catch (IOException ex) {
            log.error("[FILE STORAGE ERROR] Failed to store file=[{}]: {}", uniqueFileName, ex.getMessage());
            throw new DomainException("Could not store file. Please try again!", HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORE_ERROR");
        }
    }

    public String storeAdmitCardPdf(byte[] pdfBytes, String fileName) {
        try {
            Path targetLocation = this.admitCardStorageLocation.resolve(fileName);
            Files.write(targetLocation, pdfBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("[ADMIT CARD FILE SUCCESS] Admit Card saved: path=[{}] size=[{} bytes]", targetLocation, pdfBytes.length);
            return fileName;
        } catch (IOException ex) {
            log.error("[ADMIT CARD FILE ERROR] Failed to save admit card pdf=[{}]: {}", fileName, ex.getMessage());
            throw new DomainException("Failed to save generated admit card PDF file", HttpStatus.INTERNAL_SERVER_ERROR, "ADMIT_CARD_SAVE_ERROR");
        }
    }

    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new DomainException("Requested resume file not found", HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
            }
        } catch (MalformedURLException ex) {
            throw new DomainException("File not found or unreadable", HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
        }
    }

    public Resource loadAdmitCardResource(String fileName) {
        try {
            Path filePath = this.admitCardStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new DomainException("Requested Admit Card file not found", HttpStatus.NOT_FOUND, "ADMIT_CARD_NOT_FOUND");
            }
        } catch (MalformedURLException ex) {
            throw new DomainException("Admit Card file not found or unreadable", HttpStatus.NOT_FOUND, "ADMIT_CARD_NOT_FOUND");
        }
    }

    public StoredChatFile storeChatFile(MultipartFile file, UUID senderId) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("Uploaded chat file is empty", HttpStatus.BAD_REQUEST, "EMPTY_FILE");
        }

        if (file.getSize() > this.chatMaxFileSizeBytes) {
            long maxMb = this.chatMaxFileSizeBytes / (1024 * 1024);
            throw new DomainException("File size exceeds maximum allowed limit of " + maxMb + "MB", HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE");
        }

        String rawOriginalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(rawOriginalFilename)) {
            rawOriginalFilename = "attachment";
        }
        String originalFilename = StringUtils.cleanPath(rawOriginalFilename);

        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new DomainException("Filename contains invalid path sequence", HttpStatus.BAD_REQUEST, "INVALID_PATH_SEQUENCE");
        }

        String fileExtension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            fileExtension = originalFilename.substring(dotIndex).toLowerCase();
        }

        if (!this.allowedExtensionsSet.contains(fileExtension)) {
            throw new DomainException("File format '" + fileExtension + "' is not supported. Allowed formats: " + this.chatAllowedExtensions,
                    HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE");
        }

        String safeFileName = "chat_" + senderId.toString() + "_" + UUID.randomUUID() + fileExtension;

        try {
            Path targetLocation = this.chatStorageLocation.resolve(safeFileName).normalize();
            if (!targetLocation.startsWith(this.chatStorageLocation)) {
                throw new DomainException("Security violation: Target file path is outside allowed storage directory",
                        HttpStatus.BAD_REQUEST, "INVALID_PATH_SEQUENCE");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            String contentType = file.getContentType();
            if (!StringUtils.hasText(contentType)) {
                contentType = "application/octet-stream";
            }

            log.info("[CHAT FILE STORAGE SUCCESS] Chat attachment saved: path=[{}] size=[{} bytes] sender=[{}]",
                    targetLocation, file.getSize(), senderId);

            return new StoredChatFile(safeFileName, originalFilename, file.getSize(), contentType);
        } catch (IOException ex) {
            log.error("[CHAT FILE STORAGE ERROR] Failed to store chat file=[{}]: {}", safeFileName, ex.getMessage());
            throw new DomainException("Could not store chat file. Please try again!", HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORE_ERROR");
        }
    }

    public Resource loadChatFileAsResource(String fileName) {
        if (!StringUtils.hasText(fileName) || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new DomainException("Invalid file name requested", HttpStatus.BAD_REQUEST, "INVALID_FILE_NAME");
        }

        try {
            Path filePath = this.chatStorageLocation.resolve(fileName).normalize();
            if (!filePath.startsWith(this.chatStorageLocation)) {
                throw new DomainException("Unauthorized file path traversal attempt", HttpStatus.FORBIDDEN, "FORBIDDEN_FILE_PATH");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new DomainException("Requested chat attachment not found", HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
            }
        } catch (MalformedURLException ex) {
            throw new DomainException("Chat file not found or unreadable", HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
        }
    }
}
