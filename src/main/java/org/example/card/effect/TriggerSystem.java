package org.example.card.effect;

import org.example.card.event.GameEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 触发器系统（S4）：战吼（上场）/亡语（阵亡）触发点的注册与派发。
 *
 * 无监听时零开销直通；静态注册表，测试用 {@link #reset()} 清理。
 */
public final class TriggerSystem {

    /** 触发点（后续加回合开始/结束、受伤等只增枚举值）。 */
    public enum TriggerPoint {
        ON_SUMMON,
        ON_DEATH
    }

    private static final List<Trigger> TRIGGERS = new ArrayList<>();

    private TriggerSystem() {
    }

    public static void register(Trigger trigger) {
        TRIGGERS.add(Objects.requireNonNull(trigger));
    }

    /** 派发：按注册顺序收集命中触发器的事件；无命中返回空表。 */
    public static List<GameEvent> fire(TriggerPoint point, GameContext ctx) {
        List<GameEvent> fired = new ArrayList<>();
        for (Trigger trigger : TRIGGERS) {
            if (trigger.point() == point && trigger.appliesTo(ctx)) {
                fired.addAll(trigger.apply(ctx));
            }
        }
        return fired;
    }

    /** 仅测试用：清空已注册触发器，避免跨测试污染。 */
    public static void reset() {
        TRIGGERS.clear();
    }
}
