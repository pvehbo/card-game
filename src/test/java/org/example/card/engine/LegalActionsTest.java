package org.example.card.engine;

import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ActionValidator 单测：合法动作唯一入口的行为锁。 */
class LegalActionsTest {

    private static PlayerState playerOf(List<Card> cards) {
        return new PlayerState("测试", new Deck(cards));
    }

    private static MinionCard minion(String name, int atk, int hp) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp);
    }

    @Test
    void summoningSicknessBlocksAttack() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard m = minion("新兵", 2, 2); // 默认召唤失调
        self.getField().add(m);

        assertFalse(ActionValidator.canAttack(self, foe, m, null));
        assertTrue(ActionValidator.legalActions(self, foe).isEmpty());
        assertEquals(Optional.of("新兵 召唤失调，本回合还不能攻击"),
                ActionValidator.rejectReason(self, foe, m, null));

        m.setSummoningSickness(false);
        assertTrue(ActionValidator.canAttack(self, foe, m, null));
        assertEquals(1, ActionValidator.legalActions(self, foe).size(), "空场只剩打脸");
        assertTrue(ActionValidator.legalActions(self, foe).get(0).isFaceHit());
    }

    @Test
    void attackedMinionCannotStrikeTwice() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard m = minion("打手", 3, 3);
        m.setSummoningSickness(false);
        m.setAttackedThisTurn(true);
        self.getField().add(m);

        assertFalse(ActionValidator.canAttack(self, foe, m, null));
        assertTrue(ActionValidator.legalActions(self, foe).isEmpty());
        assertEquals(Optional.of("打手 本回合已经攻击过了"),
                ActionValidator.rejectReason(self, foe, m, null));
    }

    @Test
    void fullFieldBlocksNewMinion() {
        PlayerState self = playerOf(new ArrayList<>());
        for (int i = 0; i < PlayerState.MAX_FIELD; i++) {
            MinionCard m = minion("杂兵" + i, 1, 1);
            m.setSummoningSickness(false);
            self.getField().add(m);
        }
        assertFalse(ActionValidator.canPlayMinion(self));
    }

    @Test
    void attackerGoneFromFieldIsRejected() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard ghost = minion("游魂", 2, 2);
        ghost.setSummoningSickness(false);

        assertEquals(Optional.of("该随从已不在场上"),
                ActionValidator.rejectReason(self, foe, ghost, null));
    }

    @Test
    void deadTargetIsRejected() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard m = minion("打手", 3, 3);
        m.setSummoningSickness(false);
        self.getField().add(m);
        MinionCard gone = minion("亡魂", 1, 1);

        assertEquals(Optional.of("攻击目标已不在场上"),
                ActionValidator.rejectReason(self, foe, m, gone));
    }

    /** legalActions 必须与 canAttack 完全一致：不多不少。 */
    @Test
    void legalActionsMatchesCanAttack() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard ready = minion("老兵", 3, 3);
        ready.setSummoningSickness(false);
        MinionCard sick = minion("新兵", 2, 2);
        self.getField().addAll(List.of(ready, sick));
        MinionCard d1 = minion("守1", 1, 2);
        MinionCard d2 = minion("守2", 1, 2);
        foe.getField().addAll(List.of(d1, d2));

        List<ActionValidator.Move> moves = ActionValidator.legalActions(self, foe);

        // 只有老兵可出手：2 个随从目标 + 打脸 = 3 个动作
        assertEquals(3, moves.size());
        assertTrue(moves.stream().allMatch(mv ->
                ActionValidator.canAttack(self, foe, mv.attacker(), mv.target())));
        assertTrue(moves.stream().allMatch(mv -> mv.attacker() == ready));
        // 目标按对方站位顺序，打脸最后
        assertEquals(d1, moves.get(0).target());
        assertEquals(d2, moves.get(1).target());
        assertTrue(moves.get(2).isFaceHit());
    }

    @Test
    void playLimitsMirrorTurnFlags() {
        PlayerState self = playerOf(new ArrayList<>());
        assertTrue(ActionValidator.canPlayMinion(self));
        assertTrue(ActionValidator.canPlaySpell(self));
        assertTrue(ActionValidator.canPlayPet(self));

        self.setMinionPlayed(true);
        self.setSpellPlayed(true);
        self.setPetPlayed(true);
        assertFalse(ActionValidator.canPlayMinion(self));
        assertFalse(ActionValidator.canPlaySpell(self));
        assertFalse(ActionValidator.canPlayPet(self));
    }
}
