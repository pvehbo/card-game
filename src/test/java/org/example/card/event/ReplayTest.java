package org.example.card.event;

import org.example.card.ai.SimpleAi;
import org.example.card.engine.GameEngine;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S9 事件回放：事件流 JSON 往返无损，且能据此还原终局双方生命。
 * 英雄生命满足 life = max(0, 20 - 英雄所受伤害总和)（PlayerState.damage 逐次钳制，
 * 本测试对局不用治疗牌，故无回复干扰）。
 */
class ReplayTest {

    private static PlayerState playerOf(String name) {
        return new PlayerState(name, new Deck(new ArrayList<>()));
    }

    private static MinionCard minion(String name, int atk, int hp) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp);
    }

    /** 无治疗的剧本对局：互撞 + 直击，收集全部事件。 */
    private static List<String> playScriptedGame(PlayerState alice, PlayerState bob,
                                                List<GameEvent> sink) {
        GameEngine engine = new GameEngine(new SimpleAi());
        engine.eventBus().onAny(sink::add);
        List<String> logs = new ArrayList<>();
        MinionCard a = minion("甲", 3, 4);
        a.setSummoningSickness(false);
        alice.getField().add(a);
        MinionCard b = minion("乙", 2, 2);
        b.setSummoningSickness(false);
        bob.getField().add(b);
        // 甲撞乙：乙阵亡，甲剩 2 血
        engine.attack(alice, bob, a, b, logs::add);
        // 丁直击：bob 掉 3 血
        MinionCard d = minion("丁", 3, 3);
        d.setSummoningSickness(false);
        alice.getField().add(d);
        engine.attack(alice, bob, d, null, logs::add);
        // 乙区新兵直击 alice
        MinionCard c = minion("丙", 4, 4);
        c.setSummoningSickness(false);
        bob.getField().add(c);
        engine.attack(bob, alice, c, null, logs::add);
        List<String> json = new ArrayList<>();
        for (GameEvent e : sink) {
            json.add(e.toJson());
        }
        return json;
    }

    @Test
    void eventStreamRoundTripsAndRestoresFinalLife() {
        PlayerState alice = playerOf("爱丽丝");
        PlayerState bob = playerOf("鲍勃");
        List<GameEvent> sink = new ArrayList<>();
        List<String> json = playScriptedGame(alice, bob, sink);

        assertFalseRoundTrip(json, sink);

        // 按“英雄受伤事件”重算双方生命，应与终局一致
        Map<String, Integer> heroDamage = new HashMap<>();
        for (String line : json) {
            GameEvent.JsonData parsed = GameEvent.fromJson(line);
            if (parsed.type() == GameEvent.Type.DAMAGE && parsed.defender() == null) {
                heroDamage.merge(parsed.target(), parsed.amount(), Integer::sum);
            }
        }
        assertEquals(Math.max(0, PlayerState.START_LIFE - heroDamage.getOrDefault("鲍勃", 0)),
                bob.getLifePoints());
        assertEquals(Math.max(0, PlayerState.START_LIFE - heroDamage.getOrDefault("爱丽丝", 0)),
                alice.getLifePoints());
        assertEquals(PlayerState.START_LIFE - 3, bob.getLifePoints());
        assertEquals(PlayerState.START_LIFE - 4, alice.getLifePoints());
    }

    private static void assertFalseRoundTrip(List<String> json, List<GameEvent> sink) {
        assertEquals(sink.size(), json.size());
        for (int i = 0; i < sink.size(); i++) {
            GameEvent.JsonData parsed = GameEvent.fromJson(json.get(i));
            GameEvent original = sink.get(i);
            assertEquals(original.type(), parsed.type());
            assertEquals(original.amount(), parsed.amount());
            assertEquals(original.message(), parsed.message());
            assertEquals(1, parsed.schemaVersion());
        }
        assertTrue(json.stream().allMatch(line -> line.contains("\"schemaVersion\":1")));
    }

    @Test
    void futureSchemaIsRejected() {
        PlayerState alice = playerOf("爱丽丝");
        MinionCard a = minion("甲", 3, 3);
        a.setSummoningSickness(false);
        alice.getField().add(a);
        String json = new GameEvent(GameEvent.Type.ATTACK, alice, playerOf("鲍勃"),
                null, a, null, 3, "打").toJson()
                .replace("\"schemaVersion\":1", "\"schemaVersion\":999");
        assertThrows(IllegalArgumentException.class, () -> GameEvent.fromJson(json));
    }

    @Test
    void specialCharactersRoundTrip() {
        GameEvent event = GameEvent.of(GameEvent.Type.BURN, "烧掉：\"火球\" \\ 路径\n换行");
        GameEvent.JsonData parsed = GameEvent.fromJson(event.toJson());
        assertEquals(event.message(), parsed.message());
        assertEquals(GameEvent.Type.BURN, parsed.type());
    }

    @Test
    void malformedJsonIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> GameEvent.fromJson("{\"type\":\"ATTACK\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> GameEvent.fromJson("not json"));
    }
}
