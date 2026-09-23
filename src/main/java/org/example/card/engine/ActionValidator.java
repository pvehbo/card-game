package org.example.card.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;

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

    // ============ 攻击 ============

    /** 随从是否处于“可出手”状态（不含目标合法性）。 */
    public static boolean isReadyToAttack(MinionCard attacker) {
        return !attacker.isSummoningSickness() && !attacker.isAttackedThisTurn();
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
        if (attacker.isAttackedThisTurn()) {
            return Optional.of(attacker.getName() + " 本回合已经攻击过了");
        }
        if (target != null && !foe.getField().contains(target)) {
            return Optional.of("攻击目标已不在场上");
        }
        return Optional.empty();
    }

    /**
     * 本方所有合法攻击动作。
     * 顺序稳定：按己方站位顺序；每个攻击者的目标按对方站位顺序，打脸排最后。
     */
    public static List<Move> legalActions(PlayerState self, PlayerState foe) {
        List<Move> moves = new ArrayList<>();
        for (MinionCard attacker : self.getField()) {
            if (!isReadyToAttack(attacker)) {
                continue;
            }
            for (MinionCard target : foe.getField()) {
                moves.add(new Move(attacker, target));
            }
            moves.add(new Move(attacker, null));
        }
        return moves;
    }
}
