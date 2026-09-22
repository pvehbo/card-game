package org.example.card.event;

import org.example.card.model.Card;
import org.example.card.model.MinionCard;
import org.example.card.model.PlayerState;

/**
 * 游戏事件：引擎在关键节点发出，UI 订阅后做动效与刷新。
 * 用 record 保证不可变，字段按事件类型取舍使用。
 */
public record GameEvent(
        Type type,
        PlayerState actor,
        PlayerState target,
        Card card,
        MinionCard attacker,
        MinionCard defender,
        int amount,
        String message
) {

    public enum Type {
        /** 抽牌 */
        DRAW,
        /** 手牌满，烧牌 */
        BURN,
        /** 随从上场 */
        SUMMON,
        /** 法术结算 */
        SPELL,
        /** 宠物召唤 */
        PET,
        /** 随从攻击（含直击） */
        ATTACK,
        /** 受到伤害（amount = 伤害值，target 为受击方） */
        DAMAGE,
        /** 随从阵亡 */
        DEATH,
        /** 回合开始 */
        TURN_START,
        /** 回合结束 */
        TURN_END,
        /** 分出胜负 */
        GAME_OVER
    }

    public static GameEvent of(Type type, String message) {
        return new GameEvent(type, null, null, null, null, null, 0, message);
    }

    public static GameEvent draw(PlayerState who, Card card, String message) {
        return new GameEvent(Type.DRAW, who, null, card, null, null, 0, message);
    }

    public static GameEvent summon(PlayerState who, MinionCard minion, String message) {
        return new GameEvent(Type.SUMMON, who, null, minion, null, null, 0, message);
    }

    public static GameEvent spell(PlayerState who, Card card, int amount, String message) {
        return new GameEvent(Type.SPELL, who, null, card, null, null, amount, message);
    }

    public static GameEvent pet(PlayerState who, Card pet, String message) {
        return new GameEvent(Type.PET, who, null, pet, null, null, 0, message);
    }

    public static GameEvent attack(PlayerState attackerSide, PlayerState defenderSide,
                                   MinionCard attacker, MinionCard defender, int amount, String message) {
        return new GameEvent(Type.ATTACK, attackerSide, defenderSide, null, attacker, defender, amount, message);
    }

    /** 英雄受伤：target = 受伤方，defender = null。 */
    public static GameEvent damage(PlayerState victim, int amount, String message) {
        return new GameEvent(Type.DAMAGE, null, victim, null, null, null, amount, message);
    }

    /**
     * 随从受伤：target = 随从所属玩家（用于判断是哪一侧），defender = 受伤的随从。
     * UI 据此把飘字/粒子锚定在随从身上，而不是英雄头像上。
     */
    public static GameEvent damage(PlayerState owner, MinionCard victim, int amount, String message) {
        return new GameEvent(Type.DAMAGE, null, owner, null, null, victim, amount, message);
    }

    public static GameEvent death(PlayerState owner, MinionCard minion, String message) {
        return new GameEvent(Type.DEATH, owner, null, minion, null, null, 0, message);
    }

    public static GameEvent turn(Type type, PlayerState who, String message) {
        return new GameEvent(type, who, null, null, null, null, 0, message);
    }

    public static GameEvent gameOver(PlayerState winner, String message) {
        return new GameEvent(Type.GAME_OVER, winner, null, null, null, null, 0, message);
    }
}
