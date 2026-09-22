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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * 随从互撞时，伤害事件必须带上「受伤的随从」。
     * 界面靠 defender 把伤害飘字/粒子锚在随从身上，否则互撞的伤害会显示在英雄头像上。
     */
    @Test
    void minionDamageEventsCarryTheMinionVictim() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("攻", 3, 3);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        MinionCard defender = minion("守", 2, 2);
        foe.getField().add(defender);

        List<org.example.card.event.GameEvent> damages = new ArrayList<>();
        engine.eventBus().on(org.example.card.event.GameEvent.Type.DAMAGE, damages::add);

        engine.playTurn(self, foe, new ArrayList<String>()::add);

        assertEquals(2, damages.size(), "互撞双方各受一次伤害");
        var toDefender = damages.stream().filter(d -> d.defender() == defender).findFirst().orElseThrow();
        assertEquals(foe, toDefender.target(), "随从受伤事件的 target 是它所属的玩家");
        assertEquals(3, toDefender.amount());

        var toAttacker = damages.stream().filter(d -> d.defender() == attacker).findFirst().orElseThrow();
        assertEquals(self, toAttacker.target());
        assertEquals(2, toAttacker.amount());
    }

    /** 英雄挨打的伤害事件不带随从，界面据此走震屏。 */
    @Test
    void heroDamageEventHasNoMinionVictim() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("攻", 4, 4);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);

        List<org.example.card.event.GameEvent> damages = new ArrayList<>();
        engine.eventBus().on(org.example.card.event.GameEvent.Type.DAMAGE, damages::add);

        engine.attack(self, foe, attacker, null, new ArrayList<String>()::add);

        assertEquals(1, damages.size());
        assertEquals(foe, damages.get(0).target());
        assertNull(damages.get(0).defender());
        assertEquals(4, damages.get(0).amount());
    }
}
