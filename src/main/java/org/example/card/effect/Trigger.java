package org.example.card.effect;

import org.example.card.event.GameEvent;

import java.util.List;

/** 条件触发器（战吼/亡语位）：触发点 + 条件 + 结算。 */
public interface Trigger {

    TriggerSystem.TriggerPoint point();

    boolean appliesTo(GameContext ctx);

    List<GameEvent> apply(GameContext ctx);
}
