package org.example.card.effect;

import java.util.List;

import org.example.card.event.GameEvent;
import org.example.card.model.Keyword;

/**
 * 内置关键词触发器（R-12）：把 BATTLECRY / DEATHRATTLE 从「占位关键词」变成真机制。
 *
 * <p>与 嘲讽 / 冲锋 一样按**关键词**定义固定效果，而不是给某张卡写特例分支——
 * 于是往 {@code cards/*.json} 加一张带该关键词的卡即可自动生效，不需要改代码。
 *
 * <ul>
 *   <li>{@code BATTLECRY} 战吼：上场时抽一张牌（走统一抽牌管线，满手会烧牌）</li>
 *   <li>{@code DEATHRATTLE} 亡语：阵亡时对敌方英雄造成 {@value #DEATHRATTLE_DAMAGE} 点伤害</li>
 * </ul>
 *
 * <p>这两个触发器的 {@code appliesTo} 都要求 {@code ctx.subject()} 是带对应关键词的随从，
 * 所以没有该关键词的卡完全不受影响（注册表里挂着也只是零开销直通）。
 */
public final class KeywordTriggers {

    /** 亡语对敌方英雄造成的伤害。 */
    public static final int DEATHRATTLE_DAMAGE = 2;

    private KeywordTriggers() {
    }

    /** 全部内置触发器（返回新实例列表，供注册表初始化）。 */
    public static List<Trigger> all() {
        return List.of(new Battlecry(), new Deathrattle());
    }

    /** 战吼：上场时抽一张牌。 */
    private static final class Battlecry implements Trigger {

        @Override
        public TriggerSystem.TriggerPoint point() {
            return TriggerSystem.TriggerPoint.ON_SUMMON;
        }

        @Override
        public boolean appliesTo(GameContext ctx) {
            return ctx.subject() != null && ctx.subject().hasKeyword(Keyword.BATTLECRY);
        }

        @Override
        public List<GameEvent> apply(GameContext ctx) {
            // 上场触发点没有敌方（playMinion/aiSummon 签名不带 foe），
            // 所以战吼只做「不需要敌方」的效果；要打敌方的效果请放在亡语位。
            GameEvent drawn = DrawEffect.drawOne(ctx.self());
            return drawn == null ? List.of() : List.of(drawn);
        }
    }

    /** 亡语：阵亡时对敌方英雄造成固定伤害。 */
    private static final class Deathrattle implements Trigger {

        @Override
        public TriggerSystem.TriggerPoint point() {
            return TriggerSystem.TriggerPoint.ON_DEATH;
        }

        @Override
        public boolean appliesTo(GameContext ctx) {
            return ctx.subject() != null
                    && ctx.subject().hasKeyword(Keyword.DEATHRATTLE)
                    && ctx.foe() != null;
        }

        @Override
        public List<GameEvent> apply(GameContext ctx) {
            ctx.foe().damage(DEATHRATTLE_DAMAGE);
            String msg = "亡语：" + ctx.subject().getName()
                    + " 对敌方英雄造成 " + DEATHRATTLE_DAMAGE + " 点伤害";
            return List.of(GameEvent.damage(ctx.foe(), DEATHRATTLE_DAMAGE, msg));
        }
    }
}
