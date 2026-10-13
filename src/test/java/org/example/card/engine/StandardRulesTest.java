package org.example.card.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.example.card.ai.RandomAi;
import org.example.card.data.CardDatabase;
import org.example.card.data.Ruleset;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.PetCard;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.Test;

/**
 * 批3标准结构规则：牌库见底递增疲劳、宠物 1 槽付费替换。
 * 经典模式保持旧行为（空堆只登记、宠物可叠加）。
 */
class StandardRulesTest {

    private PetCard petTemplate(String id) {
        for (CardDatabase.CardEntry entry : CardDatabase.entries(Ruleset.STANDARD)) {
            Card template = entry.template();
            if (template.getId().equals(id) && template instanceof PetCard pet) {
                return pet;
            }
        }
        throw new IllegalStateException("没有该宠物：" + id);
    }

    private PlayerState playerWith(Deck deck) {
        PlayerState player = new PlayerState("你", deck, 20, 10);
        for (int i = 0; i < 10; i++) {
            player.resetTurnFlags();
        }
        return player;
    }

    @Test
    void standardFatigueGrowsAndDamages() {
        List<String> logs = new ArrayList<>();
        PlayerState player = playerWith(new Deck(List.of()));
        PlayerState ai = new PlayerState("AI", new Deck(List.of()), 20, 10);
        GameEngine engine = new GameEngine(new RandomAi(), Ruleset.STANDARD);
        engine.beginAiTurn(player, logs::add);
        assertEquals(1, player.fatigue());
        assertEquals(19, player.getLifePoints());
        assertTrue(logs.stream().anyMatch(l -> l.contains("疲劳：你 牌堆已空，受 1 点疲劳伤害")));
        engine.beginAiTurn(player, logs::add);
        assertEquals(2, player.fatigue());
        assertEquals(17, player.getLifePoints());
    }

    @Test
    void classicDeckGoneKeepsOldLogOnly() {
        List<String> logs = new ArrayList<>();
        PlayerState player = playerWith(new Deck(List.of()));
        PlayerState ai = new PlayerState("AI", new Deck(List.of()), 20, 10);
        GameEngine engine = new GameEngine(new RandomAi());
        engine.beginAiTurn(player, logs::add);
        assertEquals(0, player.fatigue());
        assertEquals(20, player.getLifePoints());
        assertTrue(logs.stream().anyMatch(l -> l.contains("牌堆已空，无牌可抽")));
        assertTrue(logs.stream().noneMatch(l -> l.contains("疲劳")));
    }

    @Test
    void standardPetSlotReplacesOldPet() {
        List<String> logs = new ArrayList<>();
        PlayerState player = playerWith(new Deck(new ArrayList<>(List.of(
                petTemplate("p1").copy(), petTemplate("p2").copy()))));
        PlayerState ai = new PlayerState("AI", new Deck(new ArrayList<>()), 20, 10);
        GameEngine engine = new GameEngine(new RandomAi(), Ruleset.STANDARD);
        engine.grantSecondMoveBonus(player, 2, logs::add);
        assertEquals(2, player.getHand().size());
        PetCard first = (PetCard) player.getHand().get(0);
        assertTrue(engine.playPet(player, first, logs::add));
        assertEquals(1, player.getPets().size());
        player.resetTurnFlags();
        PetCard second = (PetCard) player.getHand().get(0);
        assertTrue(engine.playPet(player, second, logs::add));
        assertEquals(1, player.getPets().size(), "标准模式宠物仅 1 槽");
        assertEquals(second.getId(), player.getPets().get(0).getId());
        assertTrue(logs.stream().anyMatch(l -> l.contains("宠物替换：")), "旧宠离场有记录");
        assertEquals(7, player.getMana(), "替换全额付费无退费：重置回满 10，再花 3 = 7");
    }

    @Test
    void classicPetsKeepStacking() {
        List<String> logs = new ArrayList<>();
        PlayerState player = playerWith(new Deck(new ArrayList<>(List.of(
                petTemplate("p1").copy(), petTemplate("p2").copy()))));
        PlayerState ai = new PlayerState("AI", new Deck(new ArrayList<>()), 20, 10);
        GameEngine engine = new GameEngine(new RandomAi());
        engine.grantSecondMoveBonus(player, 2, logs::add);
        assertTrue(engine.playPet(player, (PetCard) player.getHand().get(0), logs::add));
        player.resetTurnFlags();
        assertTrue(engine.playPet(player, (PetCard) player.getHand().get(0), logs::add));
        assertEquals(2, player.getPets().size(), "经典模式宠物可叠加");
        assertTrue(logs.stream().noneMatch(l -> l.contains("宠物替换")));
    }

    @Test
    void engineCarriesItsRuleset() {
        assertEquals(Ruleset.STANDARD, new GameEngine(new RandomAi(), Ruleset.STANDARD).ruleset());
        assertEquals(Ruleset.CLASSIC, new GameEngine(new RandomAi(), null).ruleset());
    }
}
