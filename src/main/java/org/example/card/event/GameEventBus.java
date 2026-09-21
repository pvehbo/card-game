package org.example.card.event;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 极简事件总线：UI 按类型订阅游戏事件，引擎只负责发布。
 * 单个订阅者抛异常不影响其他订阅者与游戏流程。
 */
public class GameEventBus {

    private final Map<GameEvent.Type, List<Consumer<GameEvent>>> listeners = new EnumMap<>(GameEvent.Type.class);
    private final List<Consumer<GameEvent>> anyListeners = new ArrayList<>();

    /** 订阅某一类事件。 */
    public void on(GameEvent.Type type, Consumer<GameEvent> listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);
    }

    /** 订阅所有事件（用于写日志/调试）。 */
    public void onAny(Consumer<GameEvent> listener) {
        anyListeners.add(listener);
    }

    public void publish(GameEvent event) {
        for (Consumer<GameEvent> listener : anyListeners) {
            safeAccept(listener, event);
        }
        List<Consumer<GameEvent>> typed = listeners.get(event.type());
        if (typed != null) {
            for (Consumer<GameEvent> listener : typed) {
                safeAccept(listener, event);
            }
        }
    }

    private void safeAccept(Consumer<GameEvent> listener, GameEvent event) {
        try {
            listener.accept(event);
        } catch (RuntimeException ex) {
            System.err.println("[事件订阅者异常] " + event.type() + ": " + ex);
        }
    }
}
