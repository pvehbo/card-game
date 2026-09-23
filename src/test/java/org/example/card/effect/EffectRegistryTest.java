package org.example.card.effect;

import org.example.card.event.GameEvent;
import org.example.card.model.Card;
import org.example.card.model.Deck;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** EffectRegistry 单测：三法术注册就位、结算与原来逐行一致、重复注册炸。 */
class EffectRegistryTest {

    private static PlayerState playerOf(List<Card> cards) {
        return new PlayerState("测试", new Deck(cards));
    }

    @Test
    void allThreeSpellEffectsRegistered() {
        assertEquals(3, EffectRegistry.registered().size());
        assertTrue(EffectRegistry.registered().stream()
                .map(Effect::keyword).toList().containsAll(
                        List.of(org.example.card.model.Keyword.DAMAGE,
                                org.example.card.model.Keyword.HEAL,
                                org.example.card.model.Keyword.DRAW)));
    }

    @Test
    void damageEffectMatchesOldSettlement() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        SpellCard fireball = new SpellCard("s", "火球术", "打3", SpellCard.Kind.DAMAGE, 3);

        EffectResult result = EffectRegistry.resolve(fireball, self, foe);

        assertEquals(List.of(GameEvent.Type.SPELL, GameEvent.Type.DAMAGE),
                result.events().stream().map(GameEvent::type).toList());
        assertEquals(List.of("法术：火球术 对敌方英雄 -3"), result.logs());
        assertEquals(PlayerState.START_LIFE - 3, foe.getLifePoints());
    }

    @Test
    void healEffectMatchesOldSettlement() {
        PlayerState self = playerOf(new ArrayList<>());
        PlayerState foe = playerOf(new ArrayList<>());
        self.damage(6);
        SpellCard heal = new SpellCard("s", "治疗之触", "回4", SpellCard.Kind.HEAL, 4);

        EffectResult result = EffectRegistry.resolve(heal, self, foe);

        assertEquals(List.of(GameEvent.Type.SPELL),
                result.events().stream().map(GameEvent::type).toList());
        assertEquals(PlayerState.START_LIFE - 2, self.getLifePoints());
    }

    @Test
    void drawEffectMatchesOldSettlement() {
        PlayerState self = playerOf(new ArrayList<>(List.of(
                new SpellCard("d1", "杂牌", "垫", SpellCard.Kind.DAMAGE, 1),
                new SpellCard("d2", "杂牌", "垫", SpellCard.Kind.DAMAGE, 1))));
        PlayerState foe = playerOf(new ArrayList<>());
        // 先摸走垫牌，牌堆见底后再结算过牌 2：抽空无事发生
        SpellCard draw = new SpellCard("s", "奥术智慧", "抽2", SpellCard.Kind.DRAW, 2);

        EffectResult result = EffectRegistry.resolve(draw, self, foe);

        assertEquals(List.of(GameEvent.Type.SPELL),
                result.events().stream().map(GameEvent::type).toList());
        assertEquals(List.of("法术：奥术智慧 抽 2 张牌"), result.logs());
        assertEquals(2, self.getHand().size());
    }

    @Test
    void duplicateRegistrationFailsFast() {
        assertThrows(IllegalStateException.class, () -> EffectRegistry.register(new DamageEffect()));
    }
}
