package org.example.card.effect;

import org.example.card.event.GameEvent;
import org.example.card.model.Keyword;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;

import java.util.List;

/** 治疗法术：回复己方英雄 amount 点生命（原 resolveSpell/HEAL 分支逐行搬迁）。 */
public final class HealEffect implements Effect {

    @Override
    public Keyword keyword() {
        return Keyword.HEAL;
    }

    @Override
    public EffectResult apply(GameContext ctx) {
        SpellCard card = (SpellCard) ctx.card();
        PlayerState self = ctx.self();
        self.heal(card.getAmount());
        String msg = "法术：" + card.getName() + " 回复己方英雄 +" + card.getAmount();
        return new EffectResult(
                List.of(GameEvent.spell(self, card, card.getAmount(), msg)),
                List.of(msg));
    }
}
