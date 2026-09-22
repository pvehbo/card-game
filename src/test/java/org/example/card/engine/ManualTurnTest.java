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

/** 玩家手动回合流程单测。 */
class ManualTurnTest {

    private static PlayerState playerOf(List<Card> cards) {
        return new PlayerState("测试", new Deck(cards));
    }

    private static MinionCard minion(String name, int atk, int hp) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp);
    }

    @Test
    void manualTurnLimitsOneOfEachCardKind() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        self.getHand().add(minion("A", 2, 2));
        self.getHand().add(minion("B", 3, 3));
        self.getHand().add(new SpellCard("s", "火球术", "打3", SpellCard.Kind.DAMAGE, 3));
        self.getHand().add(new PetCard("p", "战鼓兽", "光环", 1, 0));
        List<String> logs = new ArrayList<>();

        engine.startPlayerTurn(self, foe, logs::add);
        assertTrue(engine.playMinion(self, (MinionCard) self.getHand().get(0), logs::add));
        assertFalse(engine.playMinion(self, (MinionCard) self.getHand().get(0), logs::add),
                "同回合第二张随从应被拒绝");

        // 法术
        SpellCard spell = (SpellCard) self.getHand().stream()
                .filter(c -> c instanceof SpellCard).findFirst().orElseThrow();
        assertTrue(engine.playSpell(self, foe, spell, logs::add));
        assertEquals(PlayerState.START_LIFE - 3, foe.getLifePoints());

        // 宠物
        PetCard pet = (PetCard) self.getHand().stream()
                .filter(c -> c instanceof PetCard).findFirst().orElseThrow();
        assertTrue(engine.playPet(self, pet, logs::add));
        assertEquals(1, self.getPets().size());
    }

    @Test
    void summoningSicknessBlocksManualAttack() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard m = minion("打手", 3, 3);
        self.getField().add(m); // 刚上场：有召唤失调
        List<String> logs = new ArrayList<>();

        assertFalse(engine.attack(self, foe, m, null, logs::add),
                "召唤失调的随从不能攻击");

        // 结束回合后解除召唤失调
        engine.endPlayerTurn(self, foe, logs::add);
        assertTrue(engine.attack(self, foe, m, null, logs::add));
        assertEquals(PlayerState.START_LIFE - 3, foe.getLifePoints());
    }

    /** 每个随从每回合只能攻击 1 次（否则可以无限点同一个随从把对手打穿）。 */
    @Test
    void eachMinionMayAttackOnlyOncePerTurn() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard m = minion("打手", 3, 3);
        m.setSummoningSickness(false);
        self.getField().add(m);
        List<String> logs = new ArrayList<>();

        assertTrue(engine.attack(self, foe, m, null, logs::add));
        assertFalse(engine.attack(self, foe, m, null, logs::add),
                "同一随从本回合不能攻击第二次");
        assertEquals(PlayerState.START_LIFE - 3, foe.getLifePoints(), "只结算了一次伤害");

        // 回合结束后重新可以攻击
        engine.endPlayerTurn(self, foe, logs::add);
        assertTrue(engine.attack(self, foe, m, null, logs::add));
        assertEquals(PlayerState.START_LIFE - 6, foe.getLifePoints());
    }
}