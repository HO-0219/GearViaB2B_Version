package com.teamproject.resource.storage;

import com.teamproject.common.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NasFileStorageTest {
    @TempDir Path mountPoint;

    @Test
    void storesReadsAndDeletesThroughTheMountedPath() {
        NasFileStorage storage = new NasFileStorage(mountPoint.toString());

        storage.put("docs/a.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");

        FileStorage.StoredFile stored = storage.get("docs/a.txt");
        assertThat(new String(stored.content(), StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(mountPoint.resolve("docs/a.txt")).exists();

        storage.delete("docs/a.txt");
        assertThat(mountPoint.resolve("docs/a.txt")).doesNotExist();
    }

    @Test
    void getReturnsTheContentTypeThatWasPutEvenForAnExtensionLessKey() {
        NasFileStorage storage = new NasFileStorage(mountPoint.toString());

        storage.put("branding/logo", "png-bytes".getBytes(StandardCharsets.UTF_8), "image/png");

        assertThat(storage.get("branding/logo").contentType()).isEqualTo("image/png");
    }

    @Test
    void listKeysReturnsEveryStoredKeyButNotItsContentTypeSidecar() {
        NasFileStorage storage = new NasFileStorage(mountPoint.toString());
        storage.put("docs/a.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");
        storage.put("branding/logo", "png-bytes".getBytes(StandardCharsets.UTF_8), "image/png");

        assertThat(storage.listKeys()).containsExactlyInAnyOrder("docs/a.txt", "branding/logo");
    }

    @Test
    void failsFastWhenMountRootIsNotConfigured() {
        assertThatThrownBy(() -> new NasFileStorage(""))
                .isInstanceOf(ApplicationException.class);
    }

    @Test
    void failsFastWhenMountRootDoesNotExist() {
        Path missing = mountPoint.resolve("not-mounted");
        assertThatThrownBy(() -> new NasFileStorage(missing.toString()))
                .isInstanceOf(ApplicationException.class);
    }
}
