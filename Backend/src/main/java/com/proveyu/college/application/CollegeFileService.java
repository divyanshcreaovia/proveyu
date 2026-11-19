package com.proveyu.college.application;

import com.proveyu.college.domain.CollegeFile;
import com.proveyu.college.infrastructure.CollegeFileRepository;
import com.proveyu.shared.error.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tenant-scoped file storage. Files are saved under {upload-dir}/{collegeId}/ with a random name,
 * extension whitelisted, size capped and magic bytes checked against the declared extension.
 */
@Service
public class CollegeFileService {

    public static final Set<String> IMAGE_EXT = Set.of(".png", ".jpg", ".jpeg");
    public static final Set<String> DOC_EXT = Set.of(".pdf", ".doc", ".docx", ".png", ".jpg", ".jpeg");
    public static final Set<String> SHEET_EXT = Set.of(".xlsx", ".xls", ".csv");
    public static final Set<String> PDF_EXT = Set.of(".pdf");

    private final CollegeFileRepository repository;
    private final Path baseDir;
    private final long maxBytes;
    private final Set<String> globallyAllowed;

    public CollegeFileService(CollegeFileRepository repository,
                              @Value("${college.file.upload-dir:uploads/college}") String uploadDir,
                              @Value("${college.file.max-file-size-bytes:10485760}") long maxBytes,
                              @Value("${college.file.allowed-extensions:.pdf,.doc,.docx,.jpg,.jpeg,.png,.xlsx,.xls,.csv}") String allowed) {
        this.repository = repository;
        this.baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        this.globallyAllowed = Arrays.stream(allowed.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    public static String extensionOf(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i).toLowerCase(Locale.ROOT);
    }

    /** Validates size / extension / signature without storing. */
    public void validate(MultipartFile file, Set<String> allowedExt) {
        if (file == null || file.isEmpty()) {
            throw new DomainException("File is empty", HttpStatus.BAD_REQUEST, "FILE_EMPTY");
        }
        if (file.getSize() > maxBytes) {
            throw new DomainException("File exceeds the maximum size of " + (maxBytes / (1024 * 1024)) + " MB", HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!allowedExt.contains(ext) || !globallyAllowed.contains(ext)) {
            throw new DomainException("File type " + (ext.isEmpty() ? "(none)" : ext) + " is not allowed. Allowed: " + String.join(", ", allowedExt),
                    HttpStatus.BAD_REQUEST, "FILE_TYPE_NOT_ALLOWED");
        }
        if (!signatureMatches(file, ext)) {
            throw new DomainException("File content does not match its extension", HttpStatus.BAD_REQUEST, "FILE_SIGNATURE_MISMATCH");
        }
    }

    @Transactional
    public CollegeFile store(UUID collegeId, UUID uploadedBy, MultipartFile file, String category, Set<String> allowedExt) {
        validate(file, allowedExt);
        String ext = extensionOf(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + ext;
        Path dir = baseDir.resolve(collegeId.toString()).normalize();
        Path target = dir.resolve(storedName).normalize();
        if (!target.startsWith(baseDir)) {
            throw new DomainException("Invalid storage path", HttpStatus.BAD_REQUEST, "INVALID_PATH");
        }
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new DomainException("Could not store file", HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORE_FAILED");
        }
        String original = file.getOriginalFilename() == null ? storedName : Paths.get(file.getOriginalFilename()).getFileName().toString();
        return repository.save(CollegeFile.builder()
                .collegeId(collegeId).uploadedBy(uploadedBy).originalName(original.length() > 255 ? original.substring(0, 255) : original)
                .storedName(storedName).contentType(contentTypeFor(ext)).sizeBytes(file.getSize()).category(category)
                .build());
    }

    /** Stores a file the server made itself (for example a generated PDF), so it needs no upload checks. */
    @Transactional
    public CollegeFile storeBytes(UUID collegeId, UUID uploadedBy, byte[] bytes, String fileName, String category) {
        String ext = extensionOf(fileName);
        String storedName = UUID.randomUUID() + ext;
        Path dir = baseDir.resolve(collegeId.toString()).normalize();
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(storedName).normalize(), bytes);
        } catch (IOException e) {
            throw new DomainException("Could not store file", HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORE_FAILED");
        }
        return repository.save(CollegeFile.builder()
                .collegeId(collegeId).uploadedBy(uploadedBy).originalName(fileName.length() > 255 ? fileName.substring(0, 255) : fileName)
                .storedName(storedName).contentType(contentTypeFor(ext)).sizeBytes((long) bytes.length).category(category)
                .build());
    }

    @Transactional(readOnly = true)
    public CollegeFile get(UUID collegeId, UUID fileId) {
        return repository.findByIdAndCollegeId(fileId, collegeId)
                .orElseThrow(() -> new DomainException("File not found", HttpStatus.NOT_FOUND, "FILE_NOT_FOUND"));
    }

    public Resource load(CollegeFile file) {
        try {
            Path p = baseDir.resolve(file.getCollegeId().toString()).resolve(file.getStoredName()).normalize();
            if (!p.startsWith(baseDir)) throw new DomainException("Invalid path", HttpStatus.BAD_REQUEST, "INVALID_PATH");
            Resource r = new UrlResource(p.toUri());
            if (!r.exists()) throw new DomainException("File content missing", HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
            return r;
        } catch (MalformedURLException e) {
            throw new DomainException("File not found", HttpStatus.NOT_FOUND, "FILE_NOT_FOUND");
        }
    }

    public static String contentTypeFor(String ext) {
        return switch (ext) {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".xls" -> "application/vnd.ms-excel";
            case ".csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }

    private boolean signatureMatches(MultipartFile file, String ext) {
        byte[] head = new byte[8];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.read(head);
        } catch (IOException e) {
            return false;
        }
        if (n < 4) return ext.equals(".csv");
        return switch (ext) {
            case ".pdf" -> head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
            case ".png" -> (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case ".jpg", ".jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
            case ".docx", ".xlsx" -> head[0] == 'P' && head[1] == 'K';
            case ".doc", ".xls" -> (head[0] & 0xFF) == 0xD0 && (head[1] & 0xFF) == 0xCF;
            case ".csv" -> true;
            default -> false;
        };
    }
}
