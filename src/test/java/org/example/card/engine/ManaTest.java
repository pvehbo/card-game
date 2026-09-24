package org.example.card.engine;

import org.example.card.ai.SimpleAi;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.MinionCard;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B-1 费用体系：法力增长/上限/扣费/不足拒绝；0 费老卡行为不变。 */
class ManaTest {

    private static PlayerState playerOf(List<Card> cards) {
        return new PlayerState("测试", new Deck(cards));
    }

    private static MinionCard minion(String name, int atk, int hp, int cost) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp, cost);
    }

    @Test
    void manaGrowsOnePerTurnCappedAtTen() {
        PlayerState self = playerOf(new ArrayList<>());
        assertEquals(0, self.getMana());

        for (int turn = 1; turn <= 12; turn++) {
            self.resetTurnFlags();
            assertEquals(Math.min(10, turn), self.getMana(), "第 " + turn + " 回合法力");
            assertEquals(Math.min(10, turn), self.getMaxMana());
        }
    }

    @Test
    void playingSpendsMana() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        self.resetTurnFlags(); // 1 点法力
        MinionCard cheap = minion("杂兵", 1, 1, 0);
        MinionCard pricey = minion("精英", 5, 5, 3);
        self.getHand().addAll(List.of(cheap, pricey));
        List<String> logs = new ArrayList<>();

        // 只有 1 点法力：3 费上不了，0 费能上
        assertFalse(engine.playMinion(self, pricey, logs::add));
        assertTrue(logs.get(logs.size() - 1).contains("费用不够"));
        assertTrue(engine.playMinion(self, cheap, logs::add));
        assertEquals(1, self.getMana(), "0 费不扣法力");
    }

    @Test
    void expensiveSpellRejectedAndManaKept() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        self.resetTurnFlags();
        self.resetTurnFlags(); // 2 点法力
        SpellCard nuke = new SpellCard("s", "核弹", "打10", SpellCard.Kind.DAMAGE, 10, 5);
        self.getHand().add(nuke);
        List<String> logs = new ArrayList<>();

        assertFalse(engine.playSpell(self, foe, nuke, logs::add));
        assertEquals(2, self.getMana(), "打不出不扣费");
        assertEquals(PlayerState.START_LIFE, foe.getLifePoints());
        assertTrue(engine.canPlaySpell(self), "次数还在");
    }

    @Test
    void petAndValidatorRespectMana() {
        PlayerState self = playerOf(new ArrayList<>());
        self.resetTurnFlags(); // 1 点
        PetCard pet = new PetCard("p", "贵族宠", "光环", 2, 2, 2);

        assertFalse(ActionValidator.canPlay(self, pet));
        assertEquals("费用不够：需要 2 点法力",
                ActionValidator.playRejectReason(self, pet).orElseThrow());
        assertTrue(ActionValidator.canPlaySpell(self), "次数口径不受费用影响");
    }

    @Test
    void zeroCostCardsBehaveAsBefore() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        engine.startPlayerTurn(self, foe, new ArrayList<String>()::add);
        self.getHand().add(minion("A", 2, 2, 0));
        List<String> logs = new ArrayList<>();

        assertTrue(engine.playMinion(self, (MinionCard) self.getHand().get(0), logs::add));
        assertEquals(1, self.getMana(), "开局 1 点法力，打 0 费不扣");
    }

    @Test
    void aiPicksAffordableMinion() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        List<String> logs = new ArrayList<>();
        engine.beginAiTurn(self, logs::add);
        self.getHand().add(minion("巨头", 7, 7, 6));
        self.getHand().add(minion("杂兵", 2, 2, 0));

        // 1 点法力：6 费巨头买不起，AI 应上 0 费杂兵而不是罚站
        assertTrue(engine.aiSummon(self, logs::add));
        assertEquals(1, self.getField().size());
        assertEquals("杂兵", self.getField().get(0).getName());
    }

    @Test
    void aiSkipsUnaffordableSpell() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        List<String> logs = new ArrayList<>();
        engine.beginAiTurn(self, logs::add);
        engine.beginAiTurn(self, logs::add); // 2 点法力
        self.getHand().add(new SpellCard("s", "核弹", "打10", SpellCard.Kind.DAMAGE, 10, 5));

        assertFalse(engine.aiSpell(self, foe, logs::add), "5 费法术 2 点法力打不出");
        assertEquals(PlayerState.START_LIFE, foe.getLifePoints());
        assertFalse(self.isSpellPlayed(), "没打出去不占法术次数");
    }
}
