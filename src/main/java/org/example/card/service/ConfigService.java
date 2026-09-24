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

    private static Path configFile() {
        return UserData.file("config.properties");
    }

    private boolean soundEnabled = true;
    private String aiLevel = "normal";
    private int aiStepGapMs = 420;
    /** 自定义牌组（逗号分隔 id，空表示标准牌堆；B-7 构筑器读写）。 */
    private String deckIds = "";

    public void load() {
        Path configFile = configFile();
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(configFile)) {
            props.load(in);
            soundEnabled = Boolean.parseBoolean(props.getProperty("sound.enabled", "true"));
            aiLevel = props.getProperty("ai.level", "normal");
            aiStepGapMs = Integer.parseInt(props.getProperty("ai.stepGapMs", "420"));
            deckIds = props.getProperty("deck.ids", "");
        } catch (IOException | NumberFormatException | SecurityException ex) {
            GameLog.warn("读取配置失败，用默认值: " + ex.getMessage());
        }
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("sound.enabled", Boolean.toString(soundEnabled));
        props.setProperty("ai.level", aiLevel);
        props.setProperty("ai.stepGapMs", Integer.toString(aiStepGapMs));
        props.setProperty("deck.ids", deckIds);
        try {
            Path configFile = configFile();
            Files.createDirectories(configFile.getParent());
            try (OutputStream out = Files.newOutputStream(configFile)) {
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

    public String getDeckIds() {
        return deckIds;
    }

    public void setDeckIds(String deckIds) {
        this.deckIds = deckIds == null ? "" : deckIds;
    }
}
