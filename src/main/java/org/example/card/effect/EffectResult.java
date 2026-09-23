package org.example.card.effect;

import org.example.card.event.GameEvent;

import java.util.List;

/** 一次效果结算的产物：事件与日志各自有序。 */
public record EffectResult(List<GameEvent> events, List<String> logs) {

    public static EffectResult empty() {
        return new EffectResult(List.of(), List.of());
    }
}
