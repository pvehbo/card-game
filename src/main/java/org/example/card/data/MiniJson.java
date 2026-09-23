package org.example.card.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简严格 JSON 解析器——仅支持本卡牌数据 schema 需要的五种语法：
 * 对象、数组、字符串（含引号/反斜杠/斜杠/退格/换页/换行/回车/制表符转义，
 * 以及 4 位十六进制 unicode 转义）、整数（不含小数/指数）。
 * 严格到拒绝：尾随逗号、重复对象键、未转义控制字符、前导零。
 * 所有语法错误抛 {@link IllegalStateException}，消息格式：文件名:行号:原因。
 * 包内可见：仅供 org.example.card.data 使用，不引入任何第三方依赖。
 */
final class MiniJson {

    private MiniJson() {
    }

    /** 解析整段 JSON 文本并返回根值（根之外不允许多余内容）。 */
    static Value parse(String text, String sourceName) {
        Parser parser = new Parser(text, sourceName);
        Value root = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            parser.fail("根值之后存在多余内容");
        }
        return root;
    }

    /** JSON 值基类：记录来源文件名与起始行号，供上层组装"文件:行:原因"错误。 */
    abstract static class Value {

        final String source;
        final int line;

        Value(String source, int line) {
            this.source = source;
            this.line = line;
        }

        boolean isObject() {
            return this instanceof Obj;
        }

        boolean isArray() {
            return this instanceof Arr;
        }

        boolean isString() {
            return this instanceof Str;
        }

        boolean isNumber() {
            return this instanceof Num;
        }

        /** 以"文件名:行号:"为前缀组装错误消息。 */
        String at(String reason) {
            return source + ":" + line + ":" + reason;
        }

        Obj asObject() {
            if (!(this instanceof Obj)) {
                throw new IllegalStateException(at("期望对象，实际为" + typeName()));
            }
            return (Obj) this;
        }

        Arr asArray() {
            if (!(this instanceof Arr)) {
                throw new IllegalStateException(at("期望数组，实际为" + typeName()));
            }
            return (Arr) this;
        }

        Str asString() {
            if (!(this instanceof Str)) {
                throw new IllegalStateException(at("期望字符串，实际为" + typeName()));
            }
            return (Str) this;
        }

        Num asNumber() {
            if (!(this instanceof Num)) {
                throw new IllegalStateException(at("期望整数，实际为" + typeName()));
            }
            return (Num) this;
        }

        private String typeName() {
            if (this instanceof Obj) {
                return "对象";
            }
            if (this instanceof Arr) {
                return "数组";
            }
            if (this instanceof Str) {
                return "字符串";
            }
            if (this instanceof Num) {
                return "整数";
            }
            return "未知类型";
        }
    }

    /** 对象：字段按出现顺序保存，并记录每个键所在行。 */
    static final class Obj extends Value {

        private final Map<String, Value> fields = new LinkedHashMap<>();
        private final Map<String, Integer> fieldLines = new LinkedHashMap<>();

        Obj(String source, int line) {
            super(source, line);
        }

        void put(String key, Value value, int keyLine) {
            fields.put(key, value);
            fieldLines.put(key, keyLine);
        }

        boolean contains(String key) {
            return fields.containsKey(key);
        }

        Value get(String key) {
            return fields.get(key);
        }

        /** 以字段所在行为前缀组装错误消息；字段缺失时回退到对象起始行。 */
        String atKey(String key, String reason) {
            Integer keyLine = fieldLines.get(key);
            return at((keyLine == null ? this.line : keyLine), reason);
        }

        private String at(int line, String reason) {
            return source + ":" + line + ":" + reason;
        }
    }

    /** 数组：元素按顺序保存。 */
    static final class Arr extends Value {

        private final List<Value> items = new ArrayList<>();

        Arr(String source, int line) {
            super(source, line);
        }

        void add(Value value) {
            items.add(value);
        }

        int size() {
            return items.size();
        }

        Value get(int index) {
            return items.get(index);
        }
    }

    /** 字符串值（已解码转义）。 */
    static final class Str extends Value {

        final String value;

        Str(String source, int line, String value) {
            super(source, line);
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    /** 整数值。 */
    static final class Num extends Value {

        final long value;

        Num(String source, int line, long value) {
            super(source, line);
            this.value = value;
        }

        long value() {
            return value;
        }
    }

    /** 手写递归下降解析器。 */
    private static final class Parser {

        private final String text;
        private final String source;
        private int pos;

        Parser(String text, String source) {
            this.text = text;
            this.source = source;
        }

        private void fail(String reason) {
            throw new IllegalStateException(source + ":" + lineAt(pos) + ":" + reason);
        }

        private int lineAt(int index) {
            int line = 1;
            for (int i = 0; i < index; i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                }
            }
            return line;
        }

        private boolean atEnd() {
            return pos >= text.length();
        }

        private char peek() {
            return text.charAt(pos);
        }

        private char advance() {
            return text.charAt(pos++);
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char c = peek();
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private void expect(char expected) {
            if (atEnd()) {
                fail("期望 '" + expected + "'，但内容已结束");
            }
            char c = advance();
            if (c != expected) {
                fail("期望 '" + expected + "'，实际为 '" + printable(c) + "'");
            }
        }

        private static String printable(char c) {
            if (c < 0x20 || c > 0x7e) {
                return String.format("\\u%04x", (int) c);
            }
            return String.valueOf(c);
        }

        Value parseValue() {
            skipWhitespace();
            if (atEnd()) {
                fail("内容为空，期望对象/数组/字符串/整数");
            }
            char c = peek();
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                int start = pos;
                return new Str(source, lineAt(start), parseString());
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber();
            }
            fail("非法字符 '" + printable(c) + "'，期望对象/数组/字符串/整数");
            return null; // 不可达
        }

        private Obj parseObject() {
            int startLine = lineAt(pos);
            expect('{');
            skipWhitespace();
            if (!atEnd() && peek() == '}') {
                pos++;
                return new Obj(source, startLine);
            }
            Obj obj = new Obj(source, startLine);
            while (true) {
                skipWhitespace();
                if (atEnd()) {
                    fail("对象未闭合，缺少 '}'");
                }
                if (peek() != '"') {
                    fail("对象键必须为字符串，实际为 '" + printable(peek()) + "'");
                }
                int keyStart = pos;
                String key = parseString();
                int keyLine = lineAt(keyStart);
                if (obj.contains(key)) {
                    fail("对象包含重复键 '" + key + "'");
                }
                skipWhitespace();
                expect(':');
                obj.put(key, parseValue(), keyLine);
                skipWhitespace();
                if (atEnd()) {
                    fail("对象未闭合，缺少 '}'");
                }
                char c = advance();
                if (c == '}') {
                    return obj;
                }
                if (c != ',') {
                    fail("对象字段间期望 ','，实际为 '" + printable(c) + "'");
                }
                skipWhitespace();
                if (!atEnd() && peek() == '}') {
                    fail("对象不允许尾随逗号");
                }
            }
        }

        private Arr parseArray() {
            int startLine = lineAt(pos);
            expect('[');
            skipWhitespace();
            if (!atEnd() && peek() == ']') {
                pos++;
                return new Arr(source, startLine);
            }
            Arr arr = new Arr(source, startLine);
            while (true) {
                arr.add(parseValue());
                skipWhitespace();
                if (atEnd()) {
                    fail("数组未闭合，缺少 ']'");
                }
                char c = advance();
                if (c == ']') {
                    return arr;
                }
                if (c != ',') {
                    fail("数组元素间期望 ','，实际为 '" + printable(c) + "'");
                }
                skipWhitespace();
                if (!atEnd() && peek() == ']') {
                    fail("数组不允许尾随逗号");
                }
            }
        }

        /** 解析已处于开引号位置的字符串，返回解码后的内容。 */
        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    fail("字符串未闭合，缺少 '\"'");
                }
                char c = advance();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        fail("字符串转义序列不完整");
                    }
                    char esc = advance();
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            appendUnicodeEscape(sb);
                            break;
                        default:
                            fail("非法转义字符 '\\" + esc + "'");
                    }
                } else {
                    if (c < 0x20) {
                        fail("字符串包含未转义的控制字符 '" + printable(c) + "'");
                    }
                    sb.append(c);
                }
            }
        }

        private void appendUnicodeEscape(StringBuilder sb) {
            if (pos + 4 > text.length()) {
                fail("\\u 转义需要 4 位十六进制数字");
            }
            String hex = text.substring(pos, pos + 4);
            int code;
            try {
                code = Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                fail("非法 \\u 转义 '" + hex + "'");
                return;
            }
            pos += 4;
            sb.append((char) code);
        }

        private Num parseNumber() {
            int start = pos;
            if (!atEnd() && peek() == '-') {
                pos++;
            }
            if (atEnd() || peek() < '0' || peek() > '9') {
                fail("整数必须至少包含一位数字");
            }
            boolean firstDigit = true;
            long value = 0;
            while (!atEnd() && peek() >= '0' && peek() <= '9') {
                char c = advance();
                int digit = c - '0';
                if (firstDigit && digit == 0 && !atEnd() && peek() >= '0' && peek() <= '9') {
                    fail("整数不允许前导零");
                }
                firstDigit = false;
                if (value > (Long.MAX_VALUE - digit) / 10) {
                    fail("整数超出 long 范围");
                }
                value = value * 10 + digit;
            }
            if (!atEnd()) {
                char c = peek();
                if (c == '.' || c == 'e' || c == 'E') {
                    fail("本 schema 仅支持整数，不支持小数或指数");
                }
            }
            if (text.charAt(start) == '-') {
                value = -value;
            }
            return new Num(source, lineAt(start), value);
        }
    }
}