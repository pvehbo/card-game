package org.example.card.ai;

import org.example.card.engine.ActionValidator;
import org.example.card.engine.CombatResolver;
import org.example.card.model.PlayerState;

import java.util.List;

/**
 * 只读对局快照（值对象，无活引用）：
 * 把 AI 决策需要的对局信息一次性拷成不可变快照，只留数字与名字，
 * 不暴露 Deck / PlayerState / Card 等可变实体——既防副作用，也方便换真 AI。
 *
 * 随从攻/血一律委托 {@link CombatResolver} 静态方法计算（含宠物光环），
 * 本类不算光环、不重算规则。
 */
public record GameView(int selfLife, int selfHandSize, int selfMana,
                       int foeLife, List<MinionInfo> foeMinions,
                       List<MinionInfo> readyAttackers) {

    /** 单个随从的快照：名字 + 当前攻击 + 当前血量（含宠物光环）+ 是否嘲讽。 */
    public record MinionInfo(String name, int attack, int currentHealth, boolean taunt) {
    }

    /** 构造时拷贝成不可变列表，getter 拿不到活引用，也改不动快照。 */
    public GameView {
        foeMinions = List.copyOf(foeMinions);
        readyAttackers = List.copyOf(readyAttackers);
    }

    /** 拍一张不可变快照；readyAttackers 只含本回合可出手的己方随从。 */
    public static GameView snapshot(PlayerState self, PlayerState foe) {
        List<MinionInfo> foeMinions = foe.getField().stream()
                .map(m -> new MinionInfo(m.getName(), CombatResolver.effectiveAttack(foe, m),
                        CombatResolver.currentHealth(foe, m),
                        m.hasKeyword(org.example.card.model.Keyword.TAUNT)))
                .toList();
        List<MinionInfo> readyAttackers = self.getField().stream()
                .filter(ActionValidator::isReadyToAttack)
                .map(m -> new MinionInfo(m.getName(), CombatResolver.effectiveAttack(self, m),
                        CombatResolver.currentHealth(self, m), false))
                .toList();
        return new GameView(self.getLifePoints(), self.getHand().size(), self.getMana(),
                foe.getLifePoints(), foeMinions, readyAttackers);
    }
}