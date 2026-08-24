package com.teamproject.resource.storage;

import com.teamproject.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Treats a pre-mounted NAS/company storage-server path (e.g. an SMB or NFS share
 * mounted by the OS at app.storage.nas-root) as a plain filesystem, reusing the
 * same put/get/delete semantics as LocalFileStorage. Protocol-level clients
 * (SMB/NFS/S3) are intentionally out of scope for this first version.
 * Constructed directly by {@link DynamicFileStorage} — not a Spring bean itself.
 */
public class NasFileStorage extends AbstractPathFileStorage {
    public NasFileStorage(String root) {
        super(verifyMounted(root));
    }

    private static Path verifyMounted(String root) {
        if (root == null || root.isBlank()) {
            throw new ApplicationException("STORAGE_NAS_ROOT_NOT_CONFIGURED", HttpStatus.INTERNAL_SERVER_ERROR,
                    "NAS 저장 경로(app.storage.nas-root)가 설정되지 않았습니다.");
        }
        Path path = Path.of(root).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new ApplicationException("STORAGE_NAS_UNAVAILABLE", HttpStatus.INTERNAL_SERVER_ERROR,
                    "NAS 마운트 경로에 접근할 수 없습니다: " + path);
        }
        return path;
    }
}
