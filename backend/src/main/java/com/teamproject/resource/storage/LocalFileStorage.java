package com.teamproject.resource.storage;

import java.nio.file.Path;

/** Constructed directly by {@link DynamicFileStorage} — not a Spring bean itself. */
public class LocalFileStorage extends AbstractPathFileStorage {
    public LocalFileStorage(String root) {
        super(Path.of(root).toAbsolutePath().normalize());
    }
}
