package org.example.card.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.example.card.engine.GameSession;
import org.example.card.event.GameEvent;

/**
 * 对局记录器（B-6）：每玩家回合开局存一个完整快照（SaveService JSON），
 * 全程另记事件流（GameEvent JSON，供取证/调试；回放走快照步进）。
 *
 * 文件格式（~/.cardgame/last-replay.txt）：每行一条，"T " 开头为回合快照，
 * "E " 开头为事件。IO 失败只记日志，绝不炸游戏流程。
 */
public final class ReplayRecorder {

    private final List<String> turnSnapshots = new ArrayList<>();
    private final List<String> eventLog = new ArrayList<>();

    /** 回合快照（玩家回合开局调一次，读档续玩与回放都从这里来）。 */
    public void captureTurn(GameSession session) {
        try {
            turnSnapshots.add(SaveService.save(session));
        } catch (RuntimeException ex) {
            GameLog.warn("记录回合快照失败: " + ex.getMessage());
        }
    }

    /** 事件流（bus.onAny 直接挂这个方法）。 */
    public void recordEvent(GameEvent event) {
        try {
            eventLog.add(event.toJson());
        } catch (RuntimeException ex) {
            GameLog.warn("记录事件失败: " + ex.getMessage());
        }
    }

    public int turns() {
        return turnSnapshots.size();
    }

    public int events() {
        return eventLog.size();
    }

    /** 还原第 i 个回合开局（i 从 0 起）。 */
    public GameSession snapshot(int index) {
        return SaveService.load(turnSnapshots.get(index));
    }

    public List<String> eventLines() {
        return List.copyOf(eventLog);
    }

    public void clear() {
        turnSnapshots.clear();
        eventLog.clear();
    }

    public void saveToFile(Path path) {
        StringBuilder out = new StringBuilder();
        for (String snapshot : turnSnapshots) {
            out.append("T ").append(snapshot).append('\n');
        }
        for (String event : eventLog) {
            out.append("E ").append(event).append('\n');
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException | SecurityException ex) {
            GameLog.warn("保存回放失败: " + ex.getMessage());
        }
    }

    public static ReplayRecorder loadFromFile(Path path) {
        ReplayRecorder recorder = new ReplayRecorder();
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException ex) {
            GameLog.warn("读取回放失败: " + ex.getMessage());
            return recorder;
        }
        for (String line : lines) {
            if (line.startsWith("T ")) {
                recorder.turnSnapshots.add(line.substring(2));
            } else if (line.startsWith("E ")) {
                recorder.eventLog.add(line.substring(2));
            }
        }
        return recorder;
    }
}
