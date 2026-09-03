package com.teamproject.resource.storage;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public final class NasMigrationService {
    private static final int MAX_FILES = 100_000;
    private static final long DEFAULT_MAX_FILE_BYTES = 256L * 1024 * 1024;
    private static final long DEFAULT_MAX_TOTAL_BYTES = 8L * 1024 * 1024 * 1024;

    private final long maxFileBytes;
    private final long maxTotalBytes;

    public NasMigrationService() {
        this(DEFAULT_MAX_FILE_BYTES, DEFAULT_MAX_TOTAL_BYTES);
    }

    NasMigrationService(long maxFileBytes, long maxTotalBytes) {
        this.maxFileBytes = maxFileBytes;
        this.maxTotalBytes = maxTotalBytes;
    }

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
                // One file resident at a time; the StoredFile is not retained past this line.
                long size = source.get(key).content().length;
                if (size > maxFileBytes) {
                    return new NasPreflight(false, keys.size(), sourceBytes, 0, 0, "", "NAS_FILE_TOO_LARGE");
                }
                sourceBytes = Math.addExact(sourceBytes, size);
                if (sourceBytes > maxTotalBytes) {
                    return new NasPreflight(false, keys.size(), sourceBytes, 0, 0, "", "NAS_DATASET_TOO_LARGE");
                }
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
        List<String> copied = new ArrayList<>();
        long verifiedBytes = 0;
        for (String key : keys) {
            byte[] expectedDigest;
            String expectedContentType;
            int expectedLength;
            try {
                FileStorage.StoredFile expected = source.get(key);
                if (expected.content().length > maxFileBytes) {
                    return cleanUp(target, copied, "NAS_FILE_TOO_LARGE");
                }
                expectedDigest = sha256(expected.content());
                expectedContentType = expected.contentType();
                expectedLength = expected.content().length;
                target.put(key, expected.content(), expected.contentType());
                copied.add(key);
            } catch (RuntimeException exception) {
                return cleanUp(target, copied, "NAS_COPY_FAILED");
            }
            try {
                FileStorage.StoredFile actual = target.get(key);
                if (!java.util.Arrays.equals(expectedDigest, sha256(actual.content()))
                        || !java.util.Objects.equals(expectedContentType, actual.contentType())) {
                    return cleanUp(target, copied, "NAS_VERIFY_FAILED");
                }
            } catch (RuntimeException exception) {
                return cleanUp(target, copied, "NAS_VERIFY_FAILED");
            }
            verifiedBytes = Math.addExact(verifiedBytes, expectedLength);
        }
        try {
            switchProvider.run();
            return new MigrationResult(true, keys.size(), verifiedBytes, null);
        } catch (RuntimeException exception) {
            // The data is fully in place; only the provider flip failed. Leave the
            // copied files — a retry re-runs the switch — and surface the failure.
            return MigrationResult.failed("NAS_SWITCH_FAILED");
        }
    }

    public MigrationResult rollback(FileStorage current, FileStorage previous, Runnable restoreProvider) {
        return migrateAndVerify(current, previous, restoreProvider);
    }

    /** Best-effort removal of the partial copy so a later retry (or abandonment) leaves no orphans. */
    private MigrationResult cleanUp(FileStorage target, List<String> copied, String failureCode) {
        for (String key : copied) {
            try {
                target.delete(key);
            } catch (RuntimeException ignored) {
                // leaving one stale file is better than aborting cleanup
            }
        }
        return MigrationResult.failed(failureCode);
    }

    private byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
