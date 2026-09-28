package com.mengsama.mod.mengsamanetmusic.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class PackagedLicenseValidationTest {
    private static final List<String> REQUIRED = List.of(
            "LICENSE-CODE-BSD-3-CLAUSE.txt",
            "LICENSE-NETMUSIC-BSD-3-CLAUSE.txt",
            "LICENSE-ASSETS-CC-BY-NC-SA-4.0.txt",
            "LICENSE-NETMUSICLISTFORGE-MIT.txt",
            "THIRD-PARTY-NOTICES.txt"
    );

    @Test
    void releaseJarContainsEveryLicenseAndNotice() throws IOException {
        Path jarPath = Path.of(System.getProperty("mengsama.releaseJar"));
        assertTrue(Files.isRegularFile(jarPath), "release JAR must exist before tests");
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            for (String entry : REQUIRED) {
                assertNotNull(jar.getJarEntry(entry), entry + " is missing from release JAR");
            }
            String metadata = new String(jar.getInputStream(jar.getJarEntry("META-INF/mods.toml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(metadata.contains("BSD-3-Clause (code)"));
            assertTrue(metadata.contains("CC BY-NC-SA 4.0 (assets)"));
            assertFalse(metadata.contains("license = \"MIT\""));
        }
    }

    @Test
    void sourceKeepsRemovedCreditEasterEggAbsent() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        try (var files = Files.walk(sourceRoot)) {
            String source = files.filter(path -> path.toString().endsWith(".java"))
                    .map(PackagedLicenseValidationTest::read)
                    .reduce("", String::concat);
            assertFalse(source.contains("感谢使用 感谢喜欢"));
            assertFalse(source.contains("rainbowColor"));
            assertFalse(source.contains("BUG反馈可联系"));
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
