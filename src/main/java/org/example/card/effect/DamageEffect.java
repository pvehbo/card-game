package org.example.card.effect;

import org.example.card.event.GameEvent;
import org.example.card.model.Keyword;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;

import java.util.List;

/** 伤害法术：对敌方英雄造成 amount 点伤害（原 resolveSpell/DAMAGE 分支逐行搬迁）。 */
public final class DamageEffect implements Effect {

    @Override
    public Keyword keyword() {
        return Keyword.DAMAGE;
    }

    @Override
    public EffectResult apply(GameContext ctx) {
        SpellCard card = (SpellCard) ctx.card();
        PlayerState self = ctx.self();
        PlayerState foe = ctx.foe();
        foe.damage(card.getAmount());
        String msg = "法术：" + card.getName() + " 对敌方英雄 -" + card.getAmount();
        return new EffectResult(
                List.of(GameEvent.spell(self, card, card.getAmount(), msg),
                        GameEvent.damage(foe, card.getAmount(), msg)),
                List.of(msg));
    }
}
