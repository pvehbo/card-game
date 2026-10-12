package org.example.card.effect;

import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;

/**
 * 效果上下文：一次效果/触发结算时的只读输入。
 *
 * foe 在带敌方视域的上场调用（playMinion(self, foe, …)/aiSummon(self, foe, …)）传敌方；
 * 老的无 foe overload 传 null——spec DAMAGE 的战吼在 null 位静默，触发器实现仍先判空。
 */
public record GameContext(PlayerState self, PlayerState foe, Card card, MinionCard subject) {
}
