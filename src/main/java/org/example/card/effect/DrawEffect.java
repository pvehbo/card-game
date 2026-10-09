package org.example.card.effect;

import org.example.card.event.GameEvent;
import org.example.card.model.Card;
import org.example.card.model.Keyword;
import org.example.card.model.PlayerState;
import org.example.card.model.SpellCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 过牌法术：抽 amount 张牌。
 *
 * <p>走统一抽牌管线 {@link #drawOne}：逐张发 DRAW / BURN 事件（与引擎 drawPhase 同源），
 * 事件顺序为 {@code SPELL → DRAW…|BURN}，与 DamageEffect 的 {@code SPELL → DAMAGE} 同构。
 */
public final class DrawEffect implements Effect {

    @Override
    public Keyword keyword() {
        return Keyword.DRAW;
    }

    /** 抽一张的唯一样板：满手烧进墓地发 BURN，否则进手牌发 DRAW；空堆返回 null。 */
    public static GameEvent drawOne(PlayerState who) {
        Optional<Card> drawn = who.getDeck().draw();
        if (drawn.isEmpty()) {
            return null;
        }
        Card card = drawn.get();
        if (who.getHand().size() >= PlayerState.MAX_HAND) {
            who.getGraveyard().add(card);
            return GameEvent.burn(who, card, "手牌已满，烧掉：" + card);
        }
        who.getHand().add(card);
        return GameEvent.draw(who, card, "抽卡：+" + card);
    }

    @Override
    public EffectResult apply(GameContext ctx) {
        SpellCard card = (SpellCard) ctx.card();
        PlayerState self = ctx.self();
        String msg = "法术：" + card.getName() + " 抽 " + card.getAmount() + " 张牌";
        List<GameEvent> events = new ArrayList<>();
        events.add(GameEvent.spell(self, card, card.getAmount(), msg));
        for (int i = 0; i < card.getAmount(); i++) {
            GameEvent drawn = drawOne(self);
            if (drawn != null) {
                events.add(drawn);
            }
        }
        return new EffectResult(events, List.of(msg));
    }
}
