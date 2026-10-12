package org.example.card.effect;

import org.example.card.event.GameEvent;
import org.example.card.model.EffectSpec;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;

import java.util.ArrayList;
import java.util.List;

/**
 * spec 效果解析（平衡方案 v0.1 批②）：把 {@link EffectSpec} 翻成一串 GameEvent。
 *
 * <p>目标口径（与法术 DamageEffect/HealEffect/DrawEffect 同构）：
 * DAMAGE → 敌方英雄、HEAL → 己方英雄、DRAW → 己方抽 amount 张（满手烧、空堆跳）。
 * 触发结算没有 SpellCard，所以 HEAL 记日志的事件借 SPELL 通道（GameEvent 没有 HEAL 类型
 * 的事件；UI 刷新主要由上场后的统一 refresh 兜底）。
 */
public final class SpecEffects {

    private SpecEffects() {
    }

    /** 战吼结算：卡上 spec 优先，否则关键词默认（抽 1）。 */
    public static List<GameEvent> battlecry(MinionCard subject, PlayerState self, PlayerState foe) {
        EffectSpec spec = subject.battlecrySpec() != null
                ? subject.battlecrySpec()
                : EffectSpec.DEFAULT_BATTLECRY;
        return resolve(spec, self, foe, "战吼：" + subject.getName() + " ");
    }

    /** 亡语结算：卡上 spec 优先，否则关键词默认（对敌英雄 2 点）。 */
    public static List<GameEvent> deathrattle(MinionCard subject, PlayerState self, PlayerState foe) {
        EffectSpec spec = subject.deathrattleSpec() != null
                ? subject.deathrattleSpec()
                : EffectSpec.DEFAULT_DEATHRATTLE;
        return resolve(spec, self, foe, "亡语：" + subject.getName() + " ");
    }

    /** 单条 spec → 事件序列；ON_SUMMON 传入的 foe 可能为 null，DAMAGE 位答案即静默零事件。 */
    public static List<GameEvent> resolve(EffectSpec spec, PlayerState self, PlayerState foe,
                                          String label) {
        return switch (spec.kind()) {
            case DAMAGE -> {
                if (foe == null) {
                    yield List.of();
                }
                foe.damage(spec.amount());
                yield List.of(GameEvent.damage(foe, spec.amount(),
                        label + "对敌方英雄造成 " + spec.amount() + " 点伤害"));
            }
            case HEAL -> {
                self.heal(spec.amount());
                // 触发位没有 SpellCard，事件借 SPELL 类型承载日志（见类注释）
                yield List.of(GameEvent.spell(self, null, spec.amount(),
                        label + "回复己方英雄 +" + spec.amount()));
            }
            case DRAW -> {
                List<GameEvent> events = new ArrayList<>();
                for (int i = 0; i < spec.amount(); i++) {
                    GameEvent drawn = DrawEffect.drawOne(self);
                    if (drawn != null) {
                        events.add(drawn);
                    }
                }
                if (events.isEmpty()) {
                    // 空堆也要留一句日志，与抽牌阶段「牌堆已空」口径一致
                    events.add(GameEvent.of(GameEvent.Type.SPELL, label + "抽牌，但牌堆已空"));
                }
                yield events;
            }
            default -> throw new AssertionError("未处理的效果 spec：" + spec.kind());
        };
    }
}
