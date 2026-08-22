package com.teamproject.resource.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage extends AbstractPathFileStorage {
    public LocalFileStorage(@Value("${app.storage.local-root:/opt/b2bgearvia/data/uploads}") String root) {
        super(Path.of(root).toAbsolutePath().normalize());
    }
}
