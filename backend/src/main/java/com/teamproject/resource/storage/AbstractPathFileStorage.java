package com.teamproject.resource.storage;

import com.teamproject.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

abstract class AbstractPathFileStorage implements FileStorage {
    private final Path root;

    protected AbstractPathFileStorage(Path root) {
        this.root = root;
    }

    @Override public void put(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temporary, content);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary); }
            Files.writeString(contentTypePath(target), contentType);
        } catch (IOException exception) { throw storageFailure(); }
    }

    @Override public StoredFile get(String key) {
        Path target = resolve(key);
        try { return new StoredFile(Files.readAllBytes(target), readContentType(target)); }
        catch (IOException exception) { throw new ApplicationException("RESOURCE_FILE_NOT_FOUND", HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."); }
    }

    @Override public void delete(String key) {
        Path target = resolve(key);
        try {
            Files.deleteIfExists(target);
            Files.deleteIfExists(contentTypePath(target));
        } catch (IOException ignored) {}
    }

    @Override public List<String> listKeys() {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().endsWith(".contenttype"))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Files.probeContentType() guesses from the filename extension, which fails for
     * storage keys with no extension (e.g. the fixed "branding/logo" key) and returns
     * null — breaking MediaType.parseMediaType() downstream. Persist what put() was
     * actually told instead of re-guessing it on read.
     */
    private String readContentType(Path target) throws IOException {
        Path sidecar = contentTypePath(target);
        if (Files.exists(sidecar)) return Files.readString(sidecar);
        String probed = Files.probeContentType(target);
        return probed != null ? probed : "application/octet-stream";
    }

    private Path contentTypePath(Path target) {
        return target.resolveSibling(target.getFileName().toString() + ".contenttype");
    }

    private Path resolve(String key) {
        Path value = root.resolve(key).normalize();
        if (!value.startsWith(root)) throw new ApplicationException("STORAGE_KEY_INVALID", HttpStatus.BAD_REQUEST, "올바르지 않은 저장 경로입니다.");
        return value;
    }

    private ApplicationException storageFailure() {
        return new ApplicationException("RESOURCE_STORAGE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "파일을 저장하지 못했습니다.");
    }
}
