package com.teamproject.resource.storage;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDateTime;

/** Singleton row persisting which {@link FileStorage} provider is active — survives restarts. */
@Entity
@Table(name = "storage_settings")
public class StorageSettings {
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;
    @Column(name = "active_provider", nullable = false, length = 20)
    private String activeProvider;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected StorageSettings() {}

    public StorageSettings(String activeProvider) {
        this.id = SINGLETON_ID;
        this.activeProvider = activeProvider;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateProvider(String activeProvider) {
        this.activeProvider = activeProvider;
        this.updatedAt = LocalDateTime.now();
    }

    public String getActiveProvider() { return activeProvider; }
}
