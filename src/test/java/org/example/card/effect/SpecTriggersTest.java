package org.example.card.effect;

import org.example.card.ai.SimpleAi;
import org.example.card.engine.GameEngine;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.EffectSpec;
import org.example.card.model.Keyword;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 批② S2：spec 驱动的战吼/亡语端到端（含 null-foe 静默与关键词缺省回退）。 */
class SpecTriggersTest {

    private static PlayerState playerOf(List<Card> cards) {
        return new PlayerState("测试", new Deck(cards));
    }

    private static MinionCard minion(String name, int atk, int hp, EffectSpec battlecry,
                                     EffectSpec deathrattle, Keyword... keywords) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp, 0, List.of(keywords),
                battlecry, deathrattle);
    }

    @Test
    void defaultBattlecryStillDrawsOne() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(List.of(minion("备用", 1, 1, null, null)));
        MinionCard herald = minion("传令", 2, 2, null, null, Keyword.BATTLECRY);
        self.getHand().add(herald);
        List<String> logs = new ArrayList<>();

        assertTrue(engine.playMinion(self, herald, logs::add));

        assertEquals(1, self.getHand().size(), "缺省战吼把备用牌抽进手上");
        assertEquals("备用", self.getHand().get(0).getName());
    }

    @Test
    void battlecrySpecDamageHitsEnemyHero() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard striker = minion("火袭", 2, 2, new EffectSpec(Keyword.DAMAGE, 3), null,
                Keyword.BATTLECRY);
        self.getHand().add(striker);
        List<String> logs = new ArrayList<>();

        assertTrue(engine.playMinion(self, foe, striker, logs::add), "带敌方视域上场");

        assertEquals(PlayerState.START_LIFE - 3, foe.getLifePoints(), "DAMAGE 战吼打敌英雄 3 点");
        assertTrue(logs.stream().anyMatch(l -> l.contains("战吼") && l.contains("3 点")), logs.toString());
    }

    @Test
    void battlecrySpecWithoutFoeViewIsSilent() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard striker = minion("火袭", 2, 2, new EffectSpec(Keyword.DAMAGE, 3), null,
                Keyword.BATTLECRY);
        self.getHand().add(striker);
        List<String> logs = new ArrayList<>();

        assertTrue(engine.playMinion(self, striker, logs::add), "老签名上场（无敌方视域）");

        assertEquals(PlayerState.START_LIFE, foe.getLifePoints(), "无敌方视域时 DAMAGE 战吼静默不结算");
    }

    @Test
    void battlecrySpecHealPatchesSelf() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        self.damage(4);
        MinionCard priest = minion("牧师", 1, 3, new EffectSpec(Keyword.HEAL, 2), null,
                Keyword.BATTLECRY);
        self.getHand().add(priest);
        List<String> logs = new ArrayList<>();

        assertTrue(engine.playMinion(self, priest, logs::add));

        assertEquals(PlayerState.START_LIFE - 2, self.getLifePoints(), "HEAL 战吼回自己 2 点");
        assertTrue(logs.stream().anyMatch(l -> l.contains("战吼") && l.contains("回复")), logs.toString());
    }

    @Test
    void defaultDeathrattleStillStrikesOwnerOpponent() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("攻", 5, 5, null, null);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        MinionCard egg = minion("蛋", 0, 1, null, null, Keyword.DEATHRATTLE);
        foe.getField().add(egg);
        List<String> logs = new ArrayList<>();

        engine.attack(self, foe, attacker, egg, logs::add);

        assertEquals(PlayerState.START_LIFE - 2, self.getLifePoints(),
                "蛋主人是 foe：缺省亡语打蛋主人的对手=我方英雄 2 点");
    }

    @Test
    void deathrattleSpecDrawGoesToOwner() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(List.of(minion("备一", 1, 1, null, null),
                minion("备二", 1, 2, null, null)));
        MinionCard attacker = minion("攻", 5, 5, null, null);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        MinionCard egg = minion("蛋", 0, 1, null, new EffectSpec(Keyword.DRAW, 2), Keyword.DEATHRATTLE);
        foe.getField().add(egg);
        List<String> logs = new ArrayList<>();

        engine.attack(self, foe, attacker, egg, logs::add);

        assertEquals(2, foe.getHand().size(), "DRAW 亡语归死者主人（foe）抽 2");
        List<String> names = foe.getHand().stream().map(Card::getName).sorted().toList();
        assertEquals(List.of("备一", "备二"), names, "两张备用都被抽上" );
        assertEquals(PlayerState.START_LIFE, self.getLifePoints(), "spec 亡语不打英雄");
    }
}