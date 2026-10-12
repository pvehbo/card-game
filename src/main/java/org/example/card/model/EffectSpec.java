package org.example.card.model;

import java.util.Objects;

/**
 * 效果规格（数据驱动，平衡方案 v0.1 批②）：随从从 JSON 配置读取的
 * 战吼/亡语效果（kind DAMAGE/HEAL/DRAW + amount），牌表注重不写代码特例。
 *
 * <p>缺省逻辑留给引擎侧：卡上没有 spec 时按关键词走默认效果
 * （战吼抽 1、亡语打 2，见 effect.KeywordTriggers），
 * 所以老卡 JSON 可以完全不认识这个类型。
 */
public record EffectSpec(Keyword kind, int amount) {

    /** 战吼缺省：抽 1 张。 */
    public static final EffectSpec DEFAULT_BATTLECRY = new EffectSpec(Keyword.DRAW, 1);
    /** 亡语缺省：对敌方英雄 2 点伤害（KeywordTriggers.DEATHRATTLE_DAMAGE 同值）。 */
    public static final EffectSpec DEFAULT_DEATHRATTLE = new EffectSpec(Keyword.DAMAGE, 2);

    public EffectSpec {
        Objects.requireNonNull(kind, "kind 不能为空");
        if (kind != Keyword.DAMAGE && kind != Keyword.HEAL && kind != Keyword.DRAW) {
            throw new IllegalArgumentException(
                    "随从效果 spec 的 kind 只能是 DAMAGE/HEAL/DRAW：" + kind);
        }
        if (amount < 1) {
            throw new IllegalArgumentException("效果 spec 的 amount 必须大于 0：" + amount);
        }
    }

    @Override
    public String toString() {
        return kind + "(" + amount + ")";
    }
}
