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
 *   <li>{@code BATTLECRY} 战吼：卡 JSON 带 battlecry spec 按数据结算（DAMAGE/HEAL/DRAW），
 *       否则缺省上场抽 1 张（统一抽牌管线，满手会烧牌）</li>
 *   <li>{@code DEATHRATTLE} 亡语：带 deathrattle spec 按数据结算；否则缺省对敌方英雄
 *       {@value #DEATHRATTLE_DAMAGE} 点伤害</li>
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

    /** 战吼：spec 优先（DAMAGE/HEAL/DRAW），缺省上场抽 1 张。 */
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
            // spec 优先（可打敌英雄/治疗/多抽），缺省抽 1（DrawEffect.drawOne 管线）
            return SpecEffects.battlecry(ctx.subject(), ctx.self(), ctx.foe());
        }
    }

    /** 亡语：spec 优先（DAMAGE/HEAL/DRAW），缺省对敌英雄固定伤害。 */
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
            // spec 优先（可治疗/抽牌/自定义伤害），缺省对敌英雄 2 点
            return SpecEffects.deathrattle(ctx.subject(), ctx.self(), ctx.foe());
        }
    }
}
