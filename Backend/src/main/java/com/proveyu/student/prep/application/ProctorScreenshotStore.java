package com.proveyu.student.prep.application;

import com.proveyu.shared.error.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Screenshots taken when a student leaves a mock test, kept on disk as proof (one folder per attempt). */
@Component
public class ProctorScreenshotStore {

    static final int MAX_BYTES = 2 * 1024 * 1024;

    private final Path root;

    public ProctorScreenshotStore(@Value("${prep.proctor-dir:uploads/proctor}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the proctoring screenshot folder " + root, e);
        }
    }

    /** Saves a JPEG or PNG and returns its path relative to the folder (what the database keeps). */
    public String save(UUID attemptId, UUID eventId, byte[] image) {
        String ext = extension(image);
        String rel = attemptId + "/" + eventId + "." + ext;
        try {
            Path p = resolve(rel);
            Files.createDirectories(p.getParent());
            Files.write(p, image);
            return rel;
        } catch (IOException e) {
            throw new DomainException("Could not save the screenshot", HttpStatus.INTERNAL_SERVER_ERROR, "SCREENSHOT_SAVE_FAILED");
        }
    }

    public byte[] read(String rel) {
        try {
            return Files.readAllBytes(resolve(rel));
        } catch (IOException e) {
            throw new DomainException("Screenshot not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
    }

    public void delete(String rel) {
        try {
            Files.deleteIfExists(resolve(rel));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String contentType(String rel) {
        return rel != null && rel.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    /** Only real JPEG / PNG files, up to {@link #MAX_BYTES}: checked by their first bytes, not by the name the browser sent. */
    static String extension(byte[] b) {
        if (b == null || b.length < 8) throw bad("The screenshot is empty");
        if (b.length > MAX_BYTES) throw bad("The screenshot is larger than 2 MB");
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        throw bad("The screenshot must be a JPEG or PNG image");
    }

    private Path resolve(String rel) {
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root)) throw new DomainException("Invalid path", HttpStatus.BAD_REQUEST, "BAD_PATH");
        return p;
    }

    private static DomainException bad(String message) {
        return new DomainException(message, HttpStatus.BAD_REQUEST, "BAD_SCREENSHOT");
    }
}
