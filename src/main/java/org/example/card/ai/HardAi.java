package org.example.card.ai;

import java.util.List;
import java.util.Optional;

import org.example.card.model.MinionCard;

/**
 * 困难 AI（B-4）：贪心打底（继承 SimpleAi 的随从/法术/宠物选择），只加一条
 * 斩杀直觉——场上有可出手随从能打脸且伤害≥敌方生命时，全员打脸。
 *
 * 安全说明：斩杀旗只在“无嘲讽且存在打脸选项”时生效；有嘲讽时合法动作表里
 * 本来就没有打脸，回落贪心解嘲讽（优先打血最少的嘲讽）。顺序上先出手的杂兵
 * 打脸也不亏：总伤害只增不减，斩杀位不变。
 */
public class HardAi extends SimpleAi {

    @Override
    public Optional<MinionCard> chooseAttackTarget(GameView view, List<Target> legalTargets) {
        boolean lethal = view.readyAttackers().stream()
                .anyMatch(m -> m.attack() >= view.foeLife());
        boolean taunt = view.foeMinions().stream()
                .anyMatch(GameView.MinionInfo::taunt);
        if (lethal && !taunt && !legalTargets.isEmpty()) {
            return Optional.empty();
        }
        return super.chooseAttackTarget(view, legalTargets);
    }
}
