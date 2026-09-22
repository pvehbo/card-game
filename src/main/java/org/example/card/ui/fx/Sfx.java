package org.example.card.ui.fx;

/**
 * 音效类型。
 */
public enum Sfx {
    /** 抽牌：轻微"唰"声 */
    DRAW,
    /** 出牌上场：上扬 whoosh */
    SUMMON,
    /** 法术释放：明亮魔法音 */
    SPELL,
    /** 召唤宠物：柔和铃声 */
    PET,
    /** 攻击撞击：低频冲击 */
    ATTACK,
    /** 受伤：下降刺音 */
    DAMAGE,
    /** 随从阵亡：低沉消散 */
    DEATH,
    /** 回合开始：提示音 */
    TURN,
    /** 胜利：上行琶音 */
    WIN,
    /** 失败：下行音调 */
    LOSE
}
