package org.example.card.ai;

import org.example.card.model.MinionCard;

/**
 * 攻击目标候选：目标随从（活引用，调用方要真去攻击它）+ 其当前血量
 * （含宠物光环，由 GameEngine.currentHealth 计算；血量只是 AI 排序用的偏好信息）。
 */
public record Target(MinionCard card, int currentHealth) {
}