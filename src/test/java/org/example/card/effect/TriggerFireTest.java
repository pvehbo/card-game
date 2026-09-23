package org.example.card.effect;

import org.example.card.ai.SimpleAi;
import org.example.card.engine.GameEngine;
import org.example.card.event.GameEvent;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TriggerSystem 单测：触发与不触发各 1 例，端到端走引擎真实链路。 */
class TriggerFireTest {

    @AfterEach
    void clearTriggers() {
        TriggerSystem.reset();
    }

    private static PlayerState playerOf(List<Card> cards) {
        return new PlayerState("测试", new Deck(cards));
    }

    private static MinionCard minion(String name, int atk, int hp) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp);
    }

    /** 探针触发器：指定随从上场/阵亡时追加一条召唤事件。 */
    private static Trigger probe(TriggerSystem.TriggerPoint point, String name) {
        return new Trigger() {
            @Override
            public TriggerSystem.TriggerPoint point() {
                return point;
            }

            @Override
            public boolean appliesTo(GameContext ctx) {
                return ctx.subject() != null && ctx.subject().getName().equals(name);
            }

            @Override
            public List<GameEvent> apply(GameContext ctx) {
                return List.of(GameEvent.summon(ctx.self(), ctx.subject(), "触发:" + name));
            }
        };
    }

    @Test
    void summonTriggerFiresAfterSummonEvent() {
        TriggerSystem.register(probe(TriggerSystem.TriggerPoint.ON_SUMMON, "探针"));
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        self.getHand().add(minion("探针", 2, 2));

        List<GameEvent> summons = new ArrayList<>();
        engine.eventBus().on(GameEvent.Type.SUMMON, summons::add);

        assertTrue(engine.playMinion(self, (MinionCard) self.getHand().get(0),
                new ArrayList<String>()::add));

        assertEquals(2, summons.size(), "先原上场事件，后触发事件");
        assertEquals("触发:探针", summons.get(1).message());
    }

    @Test
    void summonTriggerSkippedWhenConditionMisses() {
        TriggerSystem.register(probe(TriggerSystem.TriggerPoint.ON_SUMMON, "探针"));
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        self.getHand().add(minion("路人", 2, 2));

        List<GameEvent> summons = new ArrayList<>();
        engine.eventBus().on(GameEvent.Type.SUMMON, summons::add);

        engine.playMinion(self, (MinionCard) self.getHand().get(0), new ArrayList<String>()::add);

        assertEquals(1, summons.size(), "条件不满足不触发");
    }

    @Test
    void deathTriggerFiresAfterDeathEvent() {
        TriggerSystem.register(probe(TriggerSystem.TriggerPoint.ON_DEATH, "守"));
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("攻", 5, 5);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        MinionCard defender = minion("守", 1, 1);
        foe.getField().add(defender);

        List<String> deathProbe = new ArrayList<>();
        engine.eventBus().on(GameEvent.Type.SUMMON, e -> {
            if (e.message().startsWith("触发:")) {
                deathProbe.add(e.message());
            }
        });
        List<GameEvent.Type> types = new ArrayList<>();
        engine.eventBus().onAny(e -> types.add(e.type()));

        engine.attack(self, foe, attacker, defender, new ArrayList<String>()::add);

        assertEquals(List.of(GameEvent.Type.ATTACK, GameEvent.Type.DAMAGE,
                        GameEvent.Type.DAMAGE, GameEvent.Type.DEATH, GameEvent.Type.SUMMON),
                types, "亡语触发紧跟阵亡事件");
        assertEquals(List.of("触发:守"), deathProbe);
    }
}
