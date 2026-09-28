package org.example.card.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.card.model.Card;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;

/**
 * 合法动作唯一入口（S1 从 GameEngine 抽出，不改玩法）。
 *
 * UI 按钮置灰与 AI 走子查的是同一份规则，规则只在这里写一次：
 * GameEngine 的 canXxx 原样转调这里，报错文案与原来逐字一致。
 */
public final class ActionValidator {

    private ActionValidator() {
    }

    /** 一次攻击动作：target 为 null 表示直击敌方英雄（打脸）。 */
    public record Move(MinionCard attacker, MinionCard target) {
        public boolean isFaceHit() {
            return target == null;
        }
    }

    // ============ 出牌 ============

    /** 本回合还能上随从、且场上未满 7 格。 */
    public static boolean canPlayMinion(PlayerState self) {
        return !self.isMinionPlayed() && self.getField().size() < PlayerState.MAX_FIELD;
    }

    /** 本回合还没打过法术。 */
    public static boolean canPlaySpell(PlayerState self) {
        return !self.isSpellPlayed();
    }

    /** 本回合还没召唤过宠物。 */
    public static boolean canPlayPet(PlayerState self) {
        return !self.isPetPlayed();
    }

    /** 法力是否够打出这张牌。 */
    public static boolean canAfford(PlayerState self, Card card) {
        return self.canAfford(card.getCost());
    }

    /**
     * 打出这张牌的完整条件：次数限 + 费用（B-1）。
     * UI 置灰与结算共用这一份；次数口径（canPlayXxx）保持不变供旧逻辑复用。
     */
    public static boolean canPlay(PlayerState self, Card card) {
        if (card instanceof MinionCard) {
            return canPlayMinion(self) && canAfford(self, card);
        } else if (card instanceof SpellCard) {
            return canPlaySpell(self) && canAfford(self, card);
        } else if (card instanceof PetCard) {
            return canPlayPet(self) && canAfford(self, card);
        }
        return false;
    }

    /** 打不出时给界面的原因（次数用尽优先，费用不够其次）。 */
    public static Optional<String> playRejectReason(PlayerState self, Card card) {
        boolean countOk;
        String countMsg;
        if (card instanceof MinionCard) {
            countOk = canPlayMinion(self);
            countMsg = "无法上场：本回合已上过随从或场上已满 7 格";
        } else if (card instanceof SpellCard) {
            countOk = canPlaySpell(self);
            countMsg = "无法打出：本回合已用过法术";
        } else if (card instanceof PetCard) {
            countOk = canPlayPet(self);
            countMsg = "无法召唤：本回合已召唤过宠物";
        } else {
            return Optional.of("未知卡牌类型");
        }
        if (!countOk) {
            return Optional.of(countMsg);
        }
        if (!canAfford(self, card)) {
            return Optional.of("费用不够：需要 " + card.getCost() + " 点法力");
        }
        return Optional.empty();
    }

    // ============ 攻击 ============

    /** 对方场上的嘲讽随从（按站位顺序）。 */
    public static List<MinionCard> taunts(PlayerState foe) {
        return foe.getField().stream()
                .filter(m -> m.hasKeyword(Keyword.TAUNT))
                .toList();
    }

    /** 随从是否处于“可出手”状态（不含目标合法性）：无召唤失调且出手次数未用完（风怒 2 次）。 */
    public static boolean isReadyToAttack(MinionCard attacker) {
        return !attacker.isSummoningSickness()
                && (attacker.getAttacksUsed() == 0
                    || (attacker.hasKeyword(Keyword.WINDFURY) && attacker.getAttacksUsed() < 2));
    }

    public static boolean canAttack(PlayerState self, PlayerState foe,
                                    MinionCard attacker, MinionCard target) {
        return rejectReason(self, foe, attacker, target).isEmpty();
    }

    /**
     * “为什么不行”（与 GameEngine 原报错文案逐字一致，界面可直接展示）。
     * 为空表示合法。
     */
    public static Optional<String> rejectReason(PlayerState self, PlayerState foe,
                                                MinionCard attacker, MinionCard target) {
        if (attacker == null || !self.getField().contains(attacker)) {
            return Optional.of("该随从已不在场上");
        }
        if (attacker.isSummoningSickness()) {
            return Optional.of(attacker.getName() + " 召唤失调，本回合还不能攻击");
        }
        if (!isReadyToAttack(attacker)) {
            return Optional.of(attacker.getName() + " 本回合已经攻击过了");
        }
        if (target != null && !foe.getField().contains(target)) {
            return Optional.of("攻击目标已不在场上");
        }
        if (!taunts(foe).isEmpty()
                && (target == null || !target.hasKeyword(Keyword.TAUNT))) {
            return Optional.of("对方有嘲讽随从，必须先攻击它");
        }
        return Optional.empty();
    }

    /**
     * 本方所有合法攻击动作。
     * 顺序稳定：按己方站位顺序；每个攻击者的目标按对方站位顺序，打脸排最后。
     * 对方有嘲讽时只能打嘲讽（不许打脸）。
     */
    public static List<Move> legalActions(PlayerState self, PlayerState foe) {
        List<Move> moves = new ArrayList<>();
        List<MinionCard> tauntWall = taunts(foe);
        List<MinionCard> targets = tauntWall.isEmpty() ? foe.getField() : tauntWall;
        for (MinionCard attacker : self.getField()) {
            if (!isReadyToAttack(attacker)) {
                continue;
            }
            for (MinionCard target : targets) {
                moves.add(new Move(attacker, target));
            }
            if (tauntWall.isEmpty()) {
                moves.add(new Move(attacker, null));
            }
        }
        return moves;
    }
}
