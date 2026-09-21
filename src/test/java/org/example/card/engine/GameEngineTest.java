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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 炉石式 v1 单测：出牌次数规则 / 宠物光环 / 战斗直击。 */
class GameEngineTest {

    private static PlayerState playerOf(List<Card> cards) {
        return new PlayerState("测试", new Deck(cards));
    }

    private static MinionCard minion(String name, int atk, int hp) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp);
    }

    @Test
    void onlyOneMinionPerTurn() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        self.getHand().add(minion("A", 2, 2));
        self.getHand().add(minion("B", 3, 3));
        List<String> logs = new ArrayList<>();

        new GameEngine(new SimpleAi()).playTurn(self, foe, logs::add);

        // 每回合只上 1 张随从（攻击最高的 B），A 留在手牌
        assertEquals(1, self.getField().size());
        assertEquals("B", self.getField().get(0).getName());
        assertEquals(1, self.getHand().size());
    }

    @Test
    void petAuraBuffsOwnMinions() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard m = minion("打手", 2, 2);
        self.getField().add(m);
        m.setSummoningSickness(false);
        self.getPets().add(new PetCard("p", "战鼓兽", "光环", 1, 2));
        List<String> logs = new ArrayList<>();

        new GameEngine(new SimpleAi()).playTurn(self, foe, logs::add);

        // 光环后攻击 2+1=3，直击 20 血英雄
        assertEquals(PlayerState.START_LIFE - 3, foe.getLifePoints());
        assertEquals(4, GameEngine.effectiveMaxHealth(self, m));
    }

    @Test
    void minionsTradeAndDealDamageToEachOther() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("攻", 3, 3);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        foe.getField().add(minion("守", 2, 2));
        List<String> logs = new ArrayList<>();

        new GameEngine(new SimpleAi()).playTurn(self, foe, logs::add);

        // 3 攻打 2 血守军：守军阵亡；攻击者受 2 伤，剩 1 血存活
        assertTrue(foe.getField().isEmpty());
        assertEquals(1, self.getField().size());
        assertEquals(PlayerState.START_LIFE, foe.getLifePoints());
    }
}
