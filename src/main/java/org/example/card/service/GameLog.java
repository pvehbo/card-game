package org.example.card.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 文件日志（S8）：~/.cardgame/game.log 追加写，IO 失败只打 stderr 不抛，
 * 保证日志本身永远不炸游戏流程。
 */
public final class GameLog {

    private static final Path LOG_FILE = Path.of(
            System.getProperty("user.home"), ".cardgame", "game.log");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private GameLog() {
    }

    public static synchronized void info(String message) {
        write("INFO", message);
    }

    public static synchronized void warn(String message) {
        write("WARN", message);
    }

    private static void write(String level, String message) {
        String line = TIME.format(LocalDateTime.now()) + " [" + level + "] " + message
                + System.lineSeparator();
        try {
            Files.createDirectories(LOG_FILE.getParent());
            Files.writeString(LOG_FILE, line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | SecurityException ex) {
            System.err.println("[GameLog 写入失败] " + ex.getMessage());
        }
    }
}
