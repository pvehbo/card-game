package org.example.card.engine;

import org.example.card.ai.SimpleAi;
import org.example.card.event.GameEvent;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CombatResolver 事件契约锁：结算拆出去之后，发布顺序必须与原来逐条一致，
 * 界面全靠这个顺序播动效。
 */
class CombatEventOrderTest {

    private static PlayerState playerOf(List<Card> cards) {
        return new PlayerState("测试", new Deck(cards));
    }

    private static MinionCard minion(String name, int atk, int hp) {
        return new MinionCard("t-" + name, name, "单测卡", atk, hp);
    }

    private static List<GameEvent.Type> runAttack(PlayerState self, PlayerState foe,
                                                  MinionCard attacker, MinionCard target) {
        GameEngine engine = new GameEngine(new SimpleAi());
        List<GameEvent.Type> types = new ArrayList<>();
        engine.eventBus().onAny(e -> types.add(e.type()));
        engine.attack(self, foe, attacker, target, new ArrayList<String>()::add);
        return types;
    }

    @Test
    void directHitPublishesAttackThenDamage() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard m = minion("打手", 3, 3);
        m.setSummoningSickness(false);
        self.getField().add(m);

        assertEquals(List.of(GameEvent.Type.ATTACK, GameEvent.Type.DAMAGE),
                runAttack(self, foe, m, null));
    }

    @Test
    void tradePublishesAttackDamagesAndDeath() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        MinionCard attacker = minion("攻", 3, 3);
        attacker.setSummoningSickness(false);
        self.getField().add(attacker);
        // 守军 2 血：吃 3 点阵亡；攻击者吃 2 点存活
        foe.getField().add(minion("守", 2, 2));

        assertEquals(
                List.of(GameEvent.Type.ATTACK, GameEvent.Type.DAMAGE,
                        GameEvent.Type.DAMAGE, GameEvent.Type.DEATH),
                runAttack(self, foe, attacker, foe.getField().get(0)));
    }

    @Test
    void lethalHitEndsWithGameOver() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        foe.damage(PlayerState.START_LIFE - 2); // 剩 2 血
        MinionCard m = minion("终结者", 5, 5);
        m.setSummoningSickness(false);
        self.getField().add(m);

        assertEquals(
                List.of(GameEvent.Type.ATTACK, GameEvent.Type.DAMAGE, GameEvent.Type.GAME_OVER),
                runAttack(self, foe, m, null));
    }

    @Test
    void damageSpellPublishesSpellThenDamage() {
        GameEngine engine = new GameEngine(new SimpleAi());
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        SpellCard fireball = new SpellCard("s", "火球术", "打3", SpellCard.Kind.DAMAGE, 3);
        self.getHand().add(fireball);

        List<GameEvent.Type> types = new ArrayList<>();
        engine.eventBus().onAny(e -> types.add(e.type()));
        engine.playSpell(self, foe, fireball, new ArrayList<String>()::add);

        assertEquals(List.of(GameEvent.Type.SPELL, GameEvent.Type.DAMAGE), types);
        assertEquals(PlayerState.START_LIFE - 3, foe.getLifePoints());
    }
}
