package org.example.card.data;

import java.util.ArrayList;
import java.util.List;

import org.example.card.model.Card;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.SpellCard;

/**
 * 数值体检（B-3）：香草公式筛超标卡，只告警不拦截（配牌师定夺）。
 *
 * - 随从（cost&gt;0）：身材 + 战斗关键词×2 ≈ 2×费用+2，差值超 3 告警；
 * - 法术：伤害 amount ≤ cost+2，治疗 ≤ 2×cost，过牌张数 ≤ cost；
 * - 宠物：|攻|+|血| ≤ cost+2；
 * - 0 费基础卡免检（祖传数值，保持不动）。
 */
public final class BalanceCheck {

    private BalanceCheck() {
    }

    public static List<String> check(List<Card> deck) {
        List<String> warnings = new ArrayList<>();
        for (Card card : deck) {
            if (card.getCost() == 0) {
                continue;
            }
            if (card instanceof MinionCard m) {
                int keywords = 0;
                for (Keyword keyword : List.of(Keyword.CHARGE, Keyword.TAUNT,
                        Keyword.BATTLECRY, Keyword.DEATHRATTLE)) {
                    if (m.hasKeyword(keyword)) {
                        keywords++;
                    }
                }
                int value = m.getAttack() + m.getMaxHealth() + 2 * keywords;
                int par = 2 * m.getCost() + 2;
                if (Math.abs(value - par) > 3) {
                    warnings.add("随从超标 " + m.getId() + "：身材+关键词=" + value
                            + "，同费标准 " + par);
                }
            } else if (card instanceof SpellCard s) {
                boolean over = switch (s.getKind()) {
                    case DAMAGE -> s.getAmount() > s.getCost() + 2;
                    case HEAL -> s.getAmount() > 2 * s.getCost();
                    case DRAW -> s.getAmount() > s.getCost();
                };
                if (over) {
                    warnings.add("法术超标 " + s.getId() + "：" + s.getKind()
                            + s.getAmount() + "（" + s.getCost() + " 费）");
                }
            } else if (card instanceof PetCard p) {
                int swing = Math.abs(p.getAttackBonus()) + Math.abs(p.getHealthBonus());
                if (swing > p.getCost() + 2) {
                    warnings.add("宠物超标 " + p.getId() + "：光环摆动 " + swing
                            + "（" + p.getCost() + " 费）");
                }
            }
        }
        return warnings;
    }
}
