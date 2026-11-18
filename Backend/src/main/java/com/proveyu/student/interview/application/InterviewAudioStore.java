package com.proveyu.student.interview.application;

import com.proveyu.shared.error.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Interview audio on disk: student answers ({sessionId}/{questionId}.wav, deleted after the retention period) and the
 * spoken version of each question ({sessionId}/question-{questionId}.wav, made once by Piper and reused).
 */
@Slf4j
@Component
public class InterviewAudioStore {

    private final Path root;

    public InterviewAudioStore(@Value("${interview.audio-dir:uploads/interviews}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create interview audio folder " + root, e);
        }
    }

    /** Returns the path relative to the audio folder, which is what the database stores. */
    public String saveAnswer(UUID sessionId, UUID questionId, byte[] wav) {
        String rel = sessionId + "/" + questionId + ".wav";
        try {
            Path p = resolve(rel);
            Files.createDirectories(p.getParent());
            Files.write(p, wav);
            return rel;
        } catch (IOException e) {
            throw new DomainException("Could not save the recording", HttpStatus.INTERNAL_SERVER_ERROR, "AUDIO_SAVE_FAILED");
        }
    }

    public byte[] read(String rel) {
        try {
            return Files.readAllBytes(resolve(rel));
        } catch (IOException e) {
            throw new DomainException("Recording not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
    }

    public void delete(String rel) {
        try {
            Files.deleteIfExists(resolve(rel));
        } catch (IOException e) {
            log.warn("Could not delete interview audio {}: {}", rel, e.toString());
        }
    }

    public void deleteSession(UUID sessionId) {
        try {
            FileSystemUtils.deleteRecursively(resolve(sessionId.toString()));
        } catch (IOException e) {
            log.warn("Could not delete interview audio of {}: {}", sessionId, e.toString());
        }
    }

    /** Spoken question: cached file, or made now with the given generator. */
    public byte[] speech(UUID sessionId, UUID questionId, Supplier<byte[]> generate) {
        Path p = resolve(sessionId + "/question-" + questionId + ".wav");
        try {
            if (Files.exists(p)) return Files.readAllBytes(p);
            Files.createDirectories(p.getParent());
            byte[] wav = generate.get();
            Files.write(p, wav);
            return wav;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String rel) {
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root)) throw new DomainException("Invalid path", HttpStatus.BAD_REQUEST, "INVALID_PATH");
        return p;
    }
}
