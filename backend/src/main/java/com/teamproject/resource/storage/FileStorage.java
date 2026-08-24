package com.teamproject.resource.storage;

import java.util.List;

public interface FileStorage {
    void put(String storageKey, byte[] content, String contentType);
    StoredFile get(String storageKey);
    void delete(String storageKey);
    List<String> listKeys();
    record StoredFile(byte[] content, String contentType) {}
}
