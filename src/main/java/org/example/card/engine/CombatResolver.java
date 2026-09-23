package org.example.card.engine;

import java.util.ArrayList;
import java.util.List;

import org.example.card.effect.EffectResult;
import org.example.card.effect.EffectRegistry;
import org.example.card.effect.GameContext;
import org.example.card.effect.TriggerSystem;
import org.example.card.event.GameEvent;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;

/**
 * 战斗结算（S2 从 GameEngine 抽出，不改玩法、不改事件顺序）。
 *
 * 约定：本类负责结算数学 + 状态变更 + 文案，逐条产出事件与日志；
 * 发布（eventBus.publish）仍由 GameEngine 负责。零 JavaFX 引用。
 */
public final class CombatResolver {

    private CombatResolver() {
    }

    /** 一次结算的产物：事件与日志各自保持原 publish/log 顺序。 */
    public record Outcome(List<GameEvent> events, List<String> logs) {
    }

    /** 宠物光环叠加后的随从攻击。 */
    public static int effectiveAttack(PlayerState owner, MinionCard minion) {
        int bonus = owner.getPets().stream().mapToInt(PetCard::getAttackBonus).sum();
        return minion.getAttack() + bonus;
    }

    /** 宠物光环叠加后的随从血量上限。 */
    public static int effectiveMaxHealth(PlayerState owner, MinionCard minion) {
        int bonus = owner.getPets().stream().mapToInt(PetCard::getHealthBonus).sum();
        return minion.getMaxHealth() + bonus;
    }

    /** 随从当前血量 = 光环上限 - 已受伤害。 */
    public static int currentHealth(PlayerState owner, MinionCard minion) {
        return effectiveMaxHealth(owner, minion) - minion.getDamageTaken();
    }

    /**
     * 一次攻击/互撞的结算。调用方需先经 ActionValidator.canAttack 校验。
     * target 为 null 时直击敌方英雄。
     */
    public static Outcome strike(PlayerState self, PlayerState foe,
                                 MinionCard attacker, MinionCard target) {
        List<GameEvent> events = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        int atk = effectiveAttack(self, attacker);
        if (target == null) {
            foe.damage(atk);
            String msg = attacker.getName() + " 直击敌方英雄 -" + atk;
            logs.add(msg);
            events.add(GameEvent.attack(self, foe, attacker, null, atk, msg));
            events.add(GameEvent.damage(foe, atk, msg));
        } else {
            int defAtk = effectiveAttack(foe, target);
            target.takeDamage(atk);
            attacker.takeDamage(defAtk);
            String msg = attacker.getName() + "(" + atk + ") 与 "
                    + target.getName() + "(" + defAtk + ") 互撞";
            logs.add(msg);
            events.add(GameEvent.attack(self, foe, attacker, target, atk, msg));
            // 随从受伤：带上受害者随从，界面才能把飘字/粒子锚在随从身上
            events.add(GameEvent.damage(foe, target, atk, msg));
            events.add(GameEvent.damage(self, attacker, defAtk, msg));
            if (removeDead(foe, target, events, logs)) {
                fireDeathTriggers(self, foe, target, events, logs);
            }
            if (currentHealth(self, attacker) <= 0
                    && removeDead(self, attacker, events, logs)) {
                fireDeathTriggers(self, foe, attacker, events, logs);
            }
        }
        if (foe.isDefeated()) {
            String msg = foe.getName() + " 生命归零！";
            logs.add(msg);
            events.add(GameEvent.gameOver(self, msg));
        }
        return new Outcome(events, logs);
    }

    /** 法术结算：查注册表派发（禁止单卡特例分支）。 */
    public static EffectResult resolveSpell(SpellCard card, PlayerState self, PlayerState foe) {
        return EffectRegistry.resolve(card, self, foe);
    }

    /** 移除阵亡随从；返回是否真的阵亡（供亡语触发判断）。 */
    private static boolean removeDead(PlayerState owner, MinionCard minion,
                                      List<GameEvent> events, List<String> logs) {
        if (currentHealth(owner, minion) <= 0 && owner.getField().remove(minion)) {
            owner.getGraveyard().add(minion);
            String msg = minion.getName() + " 阵亡";
            logs.add(msg);
            events.add(GameEvent.death(owner, minion, msg));
            return true;
        }
        return false;
    }

    /** 亡语派发：紧跟阵亡事件，触发消息同步记日志。 */
    private static void fireDeathTriggers(PlayerState self, PlayerState foe, MinionCard dead,
                                          List<GameEvent> events, List<String> logs) {
        for (GameEvent e : TriggerSystem.fire(TriggerSystem.TriggerPoint.ON_DEATH,
                new GameContext(self, foe, null, dead))) {
            logs.add(e.message());
            events.add(e);
        }
    }
}
