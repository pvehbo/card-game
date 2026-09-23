package org.example.card.model;

/**
 * 卡牌关键字枚举（S5 骨架版）：现与 {@link SpellCard.Kind} 一一对应，
 * 后续步骤再扩展战斗类关键字（如剧毒/嘲讽/冲锋）。
 */
public enum Keyword {

    /** 造成伤害类效果。 */
    DAMAGE,

    /** 回复生命类效果。 */
    HEAL,

    /** 抽牌类效果。 */
    DRAW
}