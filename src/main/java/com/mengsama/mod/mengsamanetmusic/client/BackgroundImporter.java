package com.mengsama.mod.mengsamanetmusic.client;

import com.mengsama.mod.mengsamanetmusic.config.MusicPlayerUiConfig;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

 
public final class BackgroundImporter {
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif");
    private BackgroundImporter() {}

    public static String extension(Path source) {
        String name = source == null || source.getFileName() == null ? "" : source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isSupported(Path source) { return EXTENSIONS.contains(extension(source)); }

    public static Path importAtomically(Path source, Path directory) throws IOException {
        if (source == null || !Files.isRegularFile(source)) throw new IOException("选择的文件不存在或不可读");
        String ext = extension(source);
        if (!EXTENSIONS.contains(ext)) throw new IOException("不支持的图片格式，仅支持 JPG/JPEG/PNG/GIF");
        Files.createDirectories(directory);
        String actualName = "background." + ext;
        Path target = directory.resolve(actualName);
        Path temporary = Files.createTempFile(directory, ".background-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally { Files.deleteIfExists(temporary); }
    }

    public static Path importAndConfigure(Path source) throws IOException {
        Path target = importAtomically(source, MusicPlayerBackground.DIRECTORY);
        MusicPlayerUiConfig.get().backgroundFile = target.getFileName().toString();
        MusicPlayerUiConfig.save();
        return target;
    }
}
