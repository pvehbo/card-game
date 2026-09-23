package org.example.card.effect;

import org.example.card.model.Keyword;

/**
 * 效果（S4）：关键词/法术结算的唯一入口。
 *
 * 新机制只加实现类并注册，禁止在 CombatResolver 里为单卡写特例分支。
 * 实现类只产事件与日志，不碰 UI。零 JavaFX 引用。
 */
public interface Effect {

    /** 本效果处理的关键词。 */
    Keyword keyword();

    /** 是否满足触发条件（默认恒成立，条件触发由 Trigger 表达）。 */
    default boolean appliesTo(GameContext ctx) {
        return true;
    }

    /** 结算：应用状态变更，返回事件与日志。 */
    EffectResult apply(GameContext ctx);
}
