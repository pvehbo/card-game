package org.example.card.effect;

import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;

/**
 * 效果上下文：一次效果/触发结算时的只读输入。
 *
 * foe 在上场触发（ON_SUMMON）时暂为 null——playMinion/aiSummon 签名未传敌方，
 * 后续补 signatures 时再带上；触发器实现里用之前先判空。
 */
public record GameContext(PlayerState self, PlayerState foe, Card card, MinionCard subject) {
}
