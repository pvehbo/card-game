package org.example.card.engine;

/** 回合阶段（v0 简化：抽卡 -> 主要 -> 战斗 -> 结束）。 */
public enum Phase {
    DRAW,
    MAIN,
    BATTLE,
    END
}
