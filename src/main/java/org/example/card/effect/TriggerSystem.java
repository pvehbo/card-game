package org.example.card.effect;

import org.example.card.event.GameEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 触发器系统（S4）：战吼（上场）/亡语（阵亡）触发点的注册与派发。
 *
 * <p>内置关键词触发器（{@link KeywordTriggers}）常驻注册表，所以战吼/亡语是真机制；
 * 无命中触发器时零开销直通。测试用 {@link #reset()} 清掉额外注册的探针，
 * 但**不会**把内置触发器一起清掉。
 */
public final class TriggerSystem {

    /** 触发点（后续加回合开始/结束、受伤等只增枚举值）。 */
    public enum TriggerPoint {
        ON_SUMMON,
        ON_DEATH
    }

    /** 内置关键词触发器：常驻，{@link #reset()} 后依然在册。 */
    private static final List<Trigger> BUILT_IN = KeywordTriggers.all();

    private static final List<Trigger> TRIGGERS = new ArrayList<>(BUILT_IN);

    private TriggerSystem() {
    }

    /** 注册额外触发器（扩展或测试探针）。 */
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

    /**
     * 清空**额外注册**的触发器，恢复到内置关键词触发器（测试隔离用）。
     * 这样既不会残留上一个用例注册的探针，也不会误清内置的战吼/亡语。
     */
    public static void reset() {
        TRIGGERS.clear();
        TRIGGERS.addAll(BUILT_IN);
    }

    /** 当前在册触发器数量（含内置），供诊断与测试断言。 */
    public static int registered() {
        return TRIGGERS.size();
    }
}
