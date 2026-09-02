package com.teamproject.resource.storage;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class NasMigrationService {
    private static final int MAX_FILES = 100_000;

    public NasPreflight preflight(Path sourceRoot, Path targetRoot, FileStorage source) {
        Path sourcePath = sourceRoot.toAbsolutePath().normalize();
        Path targetPath = targetRoot.toAbsolutePath().normalize();
        if (sourcePath.equals(targetPath) || sourcePath.startsWith(targetPath) || targetPath.startsWith(sourcePath)) {
            return NasPreflight.failed("NAS_PATH_OVERLAP");
        }
        if (!Files.isDirectory(targetPath) || !Files.isReadable(targetPath) || !Files.isWritable(targetPath)) {
            return NasPreflight.failed("NAS_UNAVAILABLE");
        }
        try {
            List<String> keys = boundedKeys(source);
            long sourceBytes = 0;
            for (String key : keys) {
                sourceBytes = Math.addExact(sourceBytes, source.get(key).content().length);
            }
            long reserve = Math.max(1L, Math.ceilDiv(sourceBytes, 10L));
            long requiredBytes = Math.addExact(sourceBytes, reserve);
            FileStore store = Files.getFileStore(targetPath);
            long freeBytes = store.getUsableSpace();
            if (freeBytes < requiredBytes) {
                return new NasPreflight(false, keys.size(), sourceBytes, requiredBytes, freeBytes,
                        identity(store), "NAS_SPACE_INSUFFICIENT");
            }
            return new NasPreflight(true, keys.size(), sourceBytes, requiredBytes, freeBytes,
                    identity(store), null);
        } catch (IOException | RuntimeException exception) {
            return NasPreflight.failed("NAS_PREFLIGHT_FAILED");
        }
    }

    public MigrationResult migrateAndVerify(FileStorage source, FileStorage target, Runnable switchProvider) {
        List<String> keys;
        try {
            keys = boundedKeys(source);
        } catch (RuntimeException exception) {
            return MigrationResult.failed("NAS_SOURCE_LIST_FAILED");
        }
        long verifiedBytes = 0;
        for (String key : keys) {
            FileStorage.StoredFile expected;
            try {
                expected = source.get(key);
                target.put(key, expected.content(), expected.contentType());
            } catch (RuntimeException exception) {
                return MigrationResult.failed("NAS_COPY_FAILED");
            }
            try {
                FileStorage.StoredFile actual = target.get(key);
                if (!Arrays.equals(expected.content(), actual.content())
                        || !Objects.equals(expected.contentType(), actual.contentType())) {
                    return MigrationResult.failed("NAS_VERIFY_FAILED");
                }
                verifiedBytes = Math.addExact(verifiedBytes, actual.content().length);
            } catch (RuntimeException exception) {
                return MigrationResult.failed("NAS_VERIFY_FAILED");
            }
        }
        try {
            switchProvider.run();
            return new MigrationResult(true, keys.size(), verifiedBytes, null);
        } catch (RuntimeException exception) {
            return MigrationResult.failed("NAS_SWITCH_FAILED");
        }
    }

    public MigrationResult rollback(FileStorage current, FileStorage previous, Runnable restoreProvider) {
        return migrateAndVerify(current, previous, restoreProvider);
    }

    private List<String> boundedKeys(FileStorage storage) {
        List<String> keys = storage.listKeys();
        if (keys.size() > MAX_FILES) {
            throw new IllegalStateException("NAS migration file limit exceeded");
        }
        return keys;
    }

    private String identity(FileStore store) {
        return store.name() + ":" + store.type();
    }

    public record NasPreflight(boolean success, int sourceFiles, long sourceBytes, long requiredBytes,
            long targetFreeBytes, String mountIdentity, String failureCode) {
        static NasPreflight failed(String code) {
            return new NasPreflight(false, 0, 0, 0, 0, "", code);
        }
    }

    public record MigrationResult(boolean success, int verifiedFiles, long verifiedBytes, String failureCode) {
        static MigrationResult failed(String code) {
            return new MigrationResult(false, 0, 0, code);
        }
    }
}
