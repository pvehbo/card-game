package org.example.card.net;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 协议编解码（M1）：{@link NetMessage} ⇄ 一行 JSON。
 *
 * <p>与事件存档一样零第三方依赖。字段顺序固定、人类可读——联机出问题时抓一行日志就能看懂，
 * 这比省几个字节重要得多。
 *
 * <p>解析策略：先扫一遍找出 {@code kind}（不依赖字段顺序），再按种类取字段；
 * 不认识的字段直接跳过而不是报错，服务端加新字段不会打死老客户端。
 */
public final class MessageCodec {

    private static final String LABEL = "协议消息";

    private MessageCodec() {
    }

    // ============ 编码 ============

    public static String encode(NetMessage message) {
        StringBuilder out = new StringBuilder(128);
        out.append("{\"kind\":").append(Json.quote(message.kind()));
        // Java 17 没有 switch 类型模式，用 instanceof 链；sealed 接口让「漏一种」仍是编译期可见的
        if (message instanceof NetMessage.Join join) {
            out.append(",\"protocol\":").append(join.protocolVersion())
                    .append(",\"name\":").append(Json.quote(join.name()))
                    .append(",\"deckId\":").append(Json.quote(join.deckId()));
        } else if (message instanceof NetMessage.Start start) {
            out.append(",\"protocol\":").append(start.protocolVersion())
                    .append(",\"seed\":").append(start.seed())
                    .append(",\"youSeat\":").append(start.youSeat())
                    .append(",\"firstSeat\":").append(start.firstSeat())
                    .append(",\"you\":").append(Json.quote(start.you()))
                    .append(",\"opponent\":").append(Json.quote(start.opponent()))
                    .append(",\"aiLevel\":").append(Json.quote(start.aiLevel()));
        } else if (message instanceof NetMessage.Action action) {
            out.append(",\"seq\":").append(action.seq())
                    .append(",\"actor\":").append(Json.quote(action.actor()))
                    .append(",\"action\":").append(Json.quote(action.action()))
                    .append(",\"cardIndex\":").append(action.cardIndex())
                    .append(",\"fieldIndex\":").append(action.fieldIndex())
                    .append(",\"targetIndex\":").append(action.targetIndex());
        } else if (message instanceof NetMessage.Sync sync) {
            out.append(",\"seq\":").append(sync.seq())
                    .append(",\"turn\":").append(sync.turn())
                    .append(",\"actor\":").append(Json.quote(sync.actor()))
                    .append(",\"action\":").append(Json.quote(sync.action()))
                    .append(",\"cardIndex\":").append(sync.cardIndex())
                    .append(",\"fieldIndex\":").append(sync.fieldIndex())
                    .append(",\"targetIndex\":").append(sync.targetIndex());
        } else if (message instanceof NetMessage.Event event) {
            out.append(",\"seq\":").append(event.seq())
                    .append(",\"turn\":").append(event.turn())
                    .append(",\"event\":").append(rawEvent(event.eventJson()));
        } else if (message instanceof NetMessage.Ping ping) {
            out.append(",\"timestamp\":").append(ping.timestamp());
        } else if (message instanceof NetMessage.Pong pong) {
            out.append(",\"timestamp\":").append(pong.timestamp());
        } else if (message instanceof NetMessage.Failure failure) {
            out.append(",\"code\":").append(Json.quote(failure.code()))
                    .append(",\"message\":").append(Json.quote(failure.message()));
        } else {
            throw new IllegalArgumentException("未知消息类型: " + message.getClass().getName());
        }
        return out.append('}').toString();
    }

    /** 事件正文以嵌套对象直接内联；万一调用方塞了非 JSON 文本，退化为字符串字面量保证仍是合法 JSON。 */
    private static String rawEvent(String eventJson) {
        if (eventJson == null) {
            return "null";
        }
        String trimmed = eventJson.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        return Json.quote(eventJson);
    }

    // ============ 解码 ============

    public static NetMessage decode(String json) {
        Map<String, Object> f = fields(json);
        String kind = requireString(f, "kind");
        try {
            return switch (kind) {
                case Protocol.JOIN -> new NetMessage.Join(
                        optionalInt(f, "protocol", Protocol.VERSION),
                        // 名字可以不写（缺席/空白都回退成「玩家N」），所以不该是硬性字段。
                        optionalString(f, "name"),
                        optionalString(f, "deckId"));
                case Protocol.START -> new NetMessage.Start(
                        optionalInt(f, "protocol", Protocol.VERSION),
                        requireLong(f, "seed"),
                        optionalInt(f, "youSeat", 0),
                        optionalInt(f, "firstSeat", 0),
                        requireString(f, "you"),
                        optionalString(f, "opponent"),
                        optionalString(f, "aiLevel"));
                case Protocol.ACTION -> new NetMessage.Action(
                        optionalInt(f, "seq", 0),
                        requireString(f, "actor"),
                        requireString(f, "action"),
                        optionalInt(f, "cardIndex", Protocol.UNUSED),
                        optionalInt(f, "fieldIndex", Protocol.UNUSED),
                        optionalInt(f, "targetIndex", Protocol.UNUSED));
                case Protocol.SYNC -> new NetMessage.Sync(
                        optionalInt(f, "seq", 0),
                        optionalInt(f, "turn", 0),
                        requireString(f, "actor"),
                        requireString(f, "action"),
                        optionalInt(f, "cardIndex", Protocol.UNUSED),
                        optionalInt(f, "fieldIndex", Protocol.UNUSED),
                        optionalInt(f, "targetIndex", Protocol.UNUSED));
                case Protocol.EVENT -> new NetMessage.Event(
                        optionalInt(f, "seq", 0),
                        optionalInt(f, "turn", 0),
                        requireString(f, "event"));
                case Protocol.PING -> new NetMessage.Ping(optionalInt(f, "timestamp", 0));
                case Protocol.PONG -> new NetMessage.Pong(optionalInt(f, "timestamp", 0));
                case Protocol.ERROR -> new NetMessage.Failure(
                        requireString(f, "code"),
                        optionalString(f, "message"));
                default -> throw new IllegalArgumentException("未知消息种类: " + kind);
            };
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(LABEL + "字段不合法（" + kind + "）：" + ex.getMessage(), ex);
        }
    }

    /** 只扫 kind，用于在不知道种类时选择解析分支；字段顺序随意。 */
    public static String peekKind(String json) {
        Json.Reader reader = new Json.Reader(json, LABEL);
        reader.expect('{');
        boolean first = true;
        while (reader.nextEntry(first)) {
            first = false;
            String key = reader.string();
            reader.expect(':');
            if ("kind".equals(key)) {
                return reader.string();
            }
            reader.skipValue();
        }
        throw new IllegalArgumentException(LABEL + "缺 kind：" + json);
    }

    private static Map<String, Object> fields(String json) {
        Json.Reader reader = new Json.Reader(json, LABEL);
        reader.expect('{');
        Map<String, Object> out = new LinkedHashMap<>();
        boolean first = true;
        while (reader.nextEntry(first)) {
            first = false;
            String key = reader.string();
            reader.expect(':');
            out.put(key, scalarOrRaw(reader));
        }
        reader.expect('}');
        reader.end();
        return out;
    }

    /** 标量直接取值；对象/数组保留原文（事件正文就是这么搬的）。 */
    private static Object scalarOrRaw(Json.Reader reader) {
        if (reader.peek('{') || reader.peek('[')) {
            return reader.rawValue();
        }
        if (reader.peek('"')) {
            return reader.string();
        }
        String raw = reader.rawValue();
        if ("null".equals(raw)) {
            return null;
        }
        if ("true".equals(raw)) {
            return Boolean.TRUE;
        }
        if ("false".equals(raw)) {
            return Boolean.FALSE;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
            return raw;
        }
    }

    private static String requireString(Map<String, Object> f, String key) {
        Object value = f.get(key);
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException("缺少字符串字段 " + key);
        }
        return s;
    }

    /** 可缺席的字符串：没写就是 null（房间会给名字兜底），但写了就得是字符串。 */
    private static String optionalString(Map<String, Object> f, String key) {
        Object value = f.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException("字段 " + key + " 不是字符串：" + value);
    }

    private static int optionalInt(Map<String, Object> f, String key, int fallback) {
        Object value = f.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalArgumentException("字段 " + key + " 不是整数：" + value);
    }

    /** seed 允许超出 int，用 long 收；解出来是 Integer 时自动扩宽。 */
    private static long requireLong(Map<String, Object> f, String key) {
        Object value = f.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("字段 " + key + " 不是整数：" + s);
            }
        }
        throw new IllegalArgumentException("缺少整数字段 " + key);
    }

    private static boolean requireBool(Map<String, Object> f, String key) {
        Object value = f.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        throw new IllegalArgumentException("缺少布尔字段 " + key);
    }
}
