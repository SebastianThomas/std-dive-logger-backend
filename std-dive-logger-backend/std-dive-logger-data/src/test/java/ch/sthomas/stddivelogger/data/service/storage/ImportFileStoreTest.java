package ch.sthomas.stddivelogger.data.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class ImportFileStoreTest {

    @TempDir Path dir;

    @Test
    void writesReadsAndDeletesByRelativePath() throws IOException {
        final var store = new ImportFileStore(dir.toString());
        final var bytes = "<uddf/>".getBytes(StandardCharsets.UTF_8);

        store.write("7/abc", bytes);

        assertThat(store.read("7/abc")).isEqualTo(bytes);
        assertThat(Files.exists(dir.resolve("7/abc"))).isTrue();
        store.delete("7/abc");
        assertThat(store.exists("7/abc")).isFalse();
    }

    @Test
    void aContentAddressedPathIsWrittenOnceAndLeavesNoTemporaryFiles() throws IOException {
        final var store = new ImportFileStore(dir.toString());

        store.write("7/abc", new byte[] {1});
        store.write("7/abc", new byte[] {2});

        assertThat(store.read("7/abc")).containsExactly(1);
        try (final var files = Files.list(dir.resolve("7"))) {
            assertThat(files).containsExactly(dir.resolve("7/abc"));
        }
    }

    @Test
    void pathsOutsideTheDirectoryAreRefused() {
        final var store = new ImportFileStore(dir.toString());

        assertThatThrownBy(() -> store.read("../outside"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.write("", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
