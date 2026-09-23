package org.example.card.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 文件配置（S8）：~/.cardgame/config.properties，不存在/损坏时用内存默认值，
 * 读写失败只记日志不抛。界面开关（音效等）经这里持久化。
 */
public final class ConfigService {

    private static final Path CONFIG_FILE = Path.of(
            System.getProperty("user.home"), ".cardgame", "config.properties");

    private boolean soundEnabled = true;
    private String aiLevel = "normal";
    private int aiStepGapMs = 420;

    public void load() {
        if (!Files.isRegularFile(CONFIG_FILE)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
            props.load(in);
            soundEnabled = Boolean.parseBoolean(props.getProperty("sound.enabled", "true"));
            aiLevel = props.getProperty("ai.level", "normal");
            aiStepGapMs = Integer.parseInt(props.getProperty("ai.stepGapMs", "420"));
        } catch (IOException | NumberFormatException | SecurityException ex) {
            GameLog.warn("读取配置失败，用默认值: " + ex.getMessage());
        }
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("sound.enabled", Boolean.toString(soundEnabled));
        props.setProperty("ai.level", aiLevel);
        props.setProperty("ai.stepGapMs", Integer.toString(aiStepGapMs));
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
                props.store(out, "CardGame config");
            }
        } catch (IOException | SecurityException ex) {
            GameLog.warn("保存配置失败: " + ex.getMessage());
        }
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
    }

    public String getAiLevel() {
        return aiLevel;
    }

    public int getAiStepGapMs() {
        return aiStepGapMs;
    }
}
