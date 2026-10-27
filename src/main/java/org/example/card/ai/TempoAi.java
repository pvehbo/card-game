package org.example.card.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.example.card.model.MinionCard;

/**
 * 节奏评测策略（路线图 V-B / T-B5）：在“无脑交换”和“无脑打脸”之间取中间。
 *
 * <p>规则：
 * 1. 对方有嘲讽 → 先拆墙（沿用 SimpleAi 的最低血目标）；
 * 2. 无嘲讽 → 若存在“可击杀且会被反杀风险低”的随从，做这笔便宜交换；
 * 3. 否则打脸。
 *
 * <p>API 只给到合法目标表与全量快照，拿不到“当前是哪只随从出手”，
 * 因此用当前可出手随从的最大攻击/最大生命做保守估计；这是评测代理的已知近似，
 * 不用于正式对局 UI。
 */
public final class TempoAi extends SimpleAi {

    @Override
    public Optional<MinionCard> chooseAttackTarget(GameView view, List<Target> legalTargets) {
        return chooseAttackTarget(view, null, legalTargets);
    }

    @Override
    public Optional<MinionCard> chooseAttackTarget(GameView view, MinionCard attacker,
                                                   List<Target> legalTargets) {
        boolean taunt = view.foeMinions().stream().anyMatch(GameView.MinionInfo::taunt);
        if (taunt) {
            return super.chooseAttackTarget(view, legalTargets);
        }
        int attack = attacker != null ? attacker.getAttack()
                : view.readyAttackers().stream()
                        .mapToInt(GameView.MinionInfo::attack).max().orElse(0);
        int health = attacker != null
                ? Math.max(1, attacker.getMaxHealth() - attacker.getDamageTaken())
                : view.readyAttackers().stream()
                        .mapToInt(GameView.MinionInfo::currentHealth).max().orElse(0);

        Optional<Target> profitable = legalTargets.stream()
                .filter(t -> t.currentHealth() <= attack)
                .filter(t -> t.card().getAttack() < health)
                .min(Comparator.comparingInt(Target::currentHealth));
        return profitable.map(Target::card);
    }
}
