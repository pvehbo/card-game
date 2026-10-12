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
 * - 随从（cost&gt;0）：身材 + 战斗关键词×2（圣盾/风怒同权，剧毒×3）≈ 2×费用+2，
 *   差值超 3 告警；
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
                        Keyword.BATTLECRY, Keyword.DEATHRATTLE,
                        Keyword.DIVINE_SHIELD, Keyword.WINDFURY)) {
                    if (m.hasKeyword(keyword)) {
                        keywords++;
                    }
                }
                int poison = m.hasKeyword(Keyword.POISONOUS) ? 3 : 0;
                int value = m.getAttack() + m.getMaxHealth() + 2 * keywords + poison;
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

    /** 同 DeckBuilder.MAX_COPIES：构筑侧同种卡上限。 */
    public static final int MAX_SETTING_COPIES = 2;

    /**
     * 两层体检第二层（批2）：卡条目浮层——deckCopies/copyLimit 数值域。
     * 数值层（上文 check(List<Card>)）查单卡身材/效果；本方法查卡表的入堆与构筑上限是否越界。
     */
    public static List<String> checkEntries(List<CardDatabase.CardEntry> entries) {
        List<String> warnings = new ArrayList<>();
        for (CardDatabase.CardEntry entry : entries) {
            if (entry.deckCopies() < 1 || entry.deckCopies() > MAX_SETTING_COPIES) {
                warnings.add("入堆份数异常 " + entry.template().getId()
                        + "：deckCopies=" + entry.deckCopies());
            }
            if (entry.copyLimit() < 1 || entry.copyLimit() > MAX_SETTING_COPIES) {
                warnings.add("构筑上限异常 " + entry.template().getId()
                        + "：copyLimit=" + entry.copyLimit());
            }
        }
        return warnings;
    }
}
