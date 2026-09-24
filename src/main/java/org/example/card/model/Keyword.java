package org.example.card.model;

/**
 * 卡牌关键字（B-2 扩展到随从机制；DAMAGE/HEAL/DRAW 与 {@link SpellCard.Kind} 对应，
 * 法术结算继续走 EffectRegistry）。
 */
public enum Keyword {

    /** 造成伤害类效果。 */
    DAMAGE,

    /** 回复生命类效果。 */
    HEAL,

    /** 抽牌类效果。 */
    DRAW,

    /** 冲锋：上场当回合即可攻击，无视召唤失调。 */
    CHARGE,

    /** 嘲讽：敌方必须先攻击它（随从目标与打脸都被拦）。 */
    TAUNT,

    /** 战吼：上场时触发（走 TriggerSystem.ON_SUMMON，结算由注册的 Trigger 实现）。 */
    BATTLECRY,

    /** 亡语：阵亡时触发（走 TriggerSystem.ON_DEATH，结算由注册的 Trigger 实现）。 */
    DEATHRATTLE
}