package org.example.card.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.example.card.model.MinionCard;
import org.example.card.model.SpellCard;
import org.junit.jupiter.api.Test;

/** 快攻评测策略：无嘲讽必须打脸；有嘲讽先拆墙；法术优先直伤。 */
class AggroAiTest {

    private GameView view(boolean taunt) {
        return new GameView(20, 5, 5, 20,
                List.of(new GameView.MinionInfo("墙", 2, 3, taunt)),
                List.of(new GameView.MinionInfo("我", 3, 2, false)));
    }

    @Test
    void noTauntMeansFace() {
        MinionCard target = new MinionCard("t", "目标", "", 2, 3);
        List<Target> legal = List.of(new Target(target, 3));
        assertTrue(new AggroAi().chooseAttackTarget(view(false), legal).isEmpty(),
                "空 Optional = 打脸");
    }

    @Test
    void tauntMustBeClearedFirst() {
        MinionCard wall = new MinionCard("w", "墙", "", 2, 3);
        Optional<MinionCard> chosen = new AggroAi().chooseAttackTarget(
                view(true), List.of(new Target(wall, 3)));
        assertEquals(Optional.of(wall), chosen, "有嘲讽时只能选墙");
    }

    @Test
    void damageSpellPreferredOverHeal() {
        SpellCard damage = new SpellCard("d", "伤害", "", SpellCard.Kind.DAMAGE, 4, 2);
        SpellCard heal = new SpellCard("h", "治疗", "", SpellCard.Kind.HEAL, 8, 2);
        Optional<SpellCard> chosen = new AggroAi().chooseSpell(view(false), List.of(heal, damage));
        assertEquals(Optional.of(damage), chosen);
    }
}
