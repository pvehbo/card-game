package org.example.card.effect;

import org.example.card.event.GameEvent;
import org.example.card.model.Keyword;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;

import java.util.ArrayList;
import java.util.List;

/** 过牌法术：抽 amount 张牌（原 resolveSpell/DRAW 分支逐行搬迁）。 */
public final class DrawEffect implements Effect {

    @Override
    public Keyword keyword() {
        return Keyword.DRAW;
    }

    @Override
    public EffectResult apply(GameContext ctx) {
        SpellCard card = (SpellCard) ctx.card();
        PlayerState self = ctx.self();
        for (int i = 0; i < card.getAmount(); i++) {
            self.getDeck().draw().ifPresentOrElse(
                    drawn -> {
                        if (self.getHand().size() >= PlayerState.MAX_HAND) {
                            self.getGraveyard().add(drawn);
                        } else {
                            self.getHand().add(drawn);
                        }
                    },
                    () -> {
                    });
        }
        String msg = "法术：" + card.getName() + " 抽 " + card.getAmount() + " 张牌";
        return new EffectResult(
                List.of(GameEvent.spell(self, card, card.getAmount(), msg)),
                List.of(msg));
    }
}
