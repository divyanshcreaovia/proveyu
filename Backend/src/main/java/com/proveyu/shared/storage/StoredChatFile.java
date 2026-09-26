package com.proveyu.shared.storage;

public record StoredChatFile(
        String storageFileName,
        String originalFileName,
        long fileSize,
        String contentType
) {}
