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

    /** 事件序列化版本号：fromJson 拒绝更大的版本（提示升级游戏）。 */
    public static final int EVENT_SCHEMA = 1;

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

    // ============ 版本化 JSON（S9：存档/回放用，零第三方依赖） ============

    /** 解析后的事件数据（实体引用还原为名字/id，数值与消息完整保留）。 */
    public record JsonData(Type type, String actor, String target, String cardId,
                           String attacker, String defender, int amount,
                           String message, int schemaVersion) {
    }

    /**
     * 序列化为 JSON（字段固定顺序，缺失写 null）。
     * actor/target 取玩家名，card 取卡 id，attacker/defender 取随从名。
     */
    public String toJson() {
        return "{\"type\":\"" + type.name() + "\""
                + ",\"actor\":" + jsonName(actor == null ? null : actor.getName())
                + ",\"target\":" + jsonName(target == null ? null : target.getName())
                + ",\"cardId\":" + jsonName(card == null ? null : card.getId())
                + ",\"attacker\":" + jsonName(attacker == null ? null : attacker.getName())
                + ",\"defender\":" + jsonName(defender == null ? null : defender.getName())
                + ",\"amount\":" + amount
                + ",\"message\":" + jsonName(message)
                + ",\"schemaVersion\":" + EVENT_SCHEMA + "}";
    }

    /** 反解析；缺字段或版本大于 {@link #EVENT_SCHEMA} 时抛 IllegalArgumentException。 */
    public static JsonData fromJson(String json) {
        JsonParser parser = new JsonParser(json);
        parser.expect('{');
        String type = null;
        String actor = null;
        String target = null;
        String cardId = null;
        String attacker = null;
        String defender = null;
        Integer amount = null;
        String message = null;
        Integer schemaVersion = null;
        boolean first = true;
        while (!parser.peek('}')) {
            if (!first) {
                parser.expect(',');
            }
            first = false;
            String key = parser.string();
            parser.expect(':');
            switch (key) {
                case "type" -> type = parser.string();
                case "actor" -> actor = parser.nullableString();
                case "target" -> target = parser.nullableString();
                case "cardId" -> cardId = parser.nullableString();
                case "attacker" -> attacker = parser.nullableString();
                case "defender" -> defender = parser.nullableString();
                case "amount" -> amount = parser.integer();
                case "message" -> message = parser.nullableString();
                case "schemaVersion" -> schemaVersion = parser.integer();
                default -> throw new IllegalArgumentException("未知字段: " + key);
            }
        }
        parser.expect('}');
        parser.end();
        if (type == null || amount == null || message == null || schemaVersion == null) {
            throw new IllegalArgumentException("事件 JSON 缺字段: " + json);
        }
        if (schemaVersion > EVENT_SCHEMA) {
            throw new IllegalArgumentException(
                    "事件版本过新（schemaVersion=" + schemaVersion + "），请升级游戏");
        }
        Type parsedType;
        try {
            parsedType = Type.valueOf(type);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("未知事件类型: " + type, ex);
        }
        return new JsonData(parsedType, actor, target, cardId,
                attacker, defender, amount, message, schemaVersion);
    }

    private static String jsonName(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    /** 仅够本 schema 用的最小严格解析器（对象/字符串/null/整数）。 */
    private static final class JsonParser {
        private final String text;
        private int pos;

        JsonParser(String text) {
            this.text = text;
        }

        void expect(char ch) {
            skipSpaces();
            if (pos >= text.length() || text.charAt(pos) != ch) {
                throw new IllegalArgumentException(
                        "事件 JSON 语法错误（位置 " + pos + " 期望 '" + ch + "'): " + text);
            }
            pos++;
        }

        boolean peek(char ch) {
            skipSpaces();
            return pos < text.length() && text.charAt(pos) == ch;
        }

        void end() {
            skipSpaces();
            if (pos != text.length()) {
                throw new IllegalArgumentException("事件 JSON 尾部多余: " + text);
            }
        }

        String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw new IllegalArgumentException("事件 JSON 字符串未闭合: " + text);
                }
                char ch = text.charAt(pos++);
                if (ch == '"') {
                    return out.toString();
                }
                if (ch == '\\') {
                    if (pos >= text.length()) {
                        throw new IllegalArgumentException("事件 JSON 转义未闭合: " + text);
                    }
                    char esc = text.charAt(pos++);
                    out.append(switch (esc) {
                        case '"' -> '"';
                        case '\\' -> '\\';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> throw new IllegalArgumentException(
                                "事件 JSON 非法转义 \\" + esc + ": " + text);
                    });
                } else {
                    out.append(ch);
                }
            }
        }

        String nullableString() {
            skipSpaces();
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            return string();
        }

        int integer() {
            skipSpaces();
            int start = pos;
            if (pos < text.length() && text.charAt(pos) == '-') {
                pos++;
            }
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("事件 JSON 期望整数（位置 " + start + "): " + text);
            }
            return Integer.parseInt(text.substring(start, pos));
        }

        private void skipSpaces() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }
    }
}
