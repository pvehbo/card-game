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

    private static Path logFile() {
        return UserData.file("game.log");
    }
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
            Path logFile = logFile();
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | SecurityException ex) {
            System.err.println("[GameLog 写入失败] " + ex.getMessage());
        }
    }
}
