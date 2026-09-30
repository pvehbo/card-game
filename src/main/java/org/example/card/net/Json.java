package org.example.card.net;

/**
 * 极简 JSON 读写（零第三方依赖），net 包与事件存档共用的底座。
 *
 * <p>只覆盖本项目需要的子集：对象 / 字符串 / 整数 / 布尔 / null。
 * 解析端刻意支持「跳过任意嵌套值」，这样服务端新增字段时老客户端不会直接崩，
 * 协议才有向前扩展的余地。
 */
public final class Json {

    private Json() {
    }

    /** 字符串字面量；null 输出裸 null（与既有事件 JSON 的约定一致）。 */
    public static String quote(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    /** 转义但不加引号。 */
    public static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }

    /**
     * 最小严格解析器：始终带一个中文标签，报错文案直接面对开发者（联机调试靠它）。
     */
    public static final class Reader {

        private final String text;
        private final String label;
        private int pos;

        public Reader(String text) {
            this(text, "JSON");
        }

        public Reader(String text, String label) {
            this.text = text == null ? "" : text;
            this.label = label;
        }

        public String label() {
            return label;
        }

        public void expect(char ch) {
            skipSpaces();
            if (pos >= text.length() || text.charAt(pos) != ch) {
                throw error("位置 " + pos + " 期望 '" + ch + "'");
            }
            pos++;
        }

        public boolean peek(char ch) {
            skipSpaces();
            return pos < text.length() && text.charAt(pos) == ch;
        }

        public void end() {
            skipSpaces();
            if (pos != text.length()) {
                throw error("尾部多余内容");
            }
        }

        /**
         * 对象条目迭代：非首条先吃掉逗号，遇 '}' 返回 false（不消费 '}'）。
         * 调用方模式：expect('{') → while(nextEntry(first)) { first=false; key... }
         */
        public boolean nextEntry(boolean first) {
            skipSpaces();
            if (pos < text.length() && text.charAt(pos) == '}') {
                return false;
            }
            if (!first) {
                expect(',');
            }
            return true;
        }

        public String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw error("字符串未闭合");
                }
                char ch = text.charAt(pos++);
                if (ch == '"') {
                    return out.toString();
                }
                if (ch == '\\') {
                    if (pos >= text.length()) {
                        throw error("转义未闭合");
                    }
                    char esc = text.charAt(pos++);
                    out.append(switch (esc) {
                        case '"' -> '"';
                        case '\\' -> '\\';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> throw error("非法转义 \\" + esc);
                    });
                } else {
                    out.append(ch);
                }
            }
        }

        public String nullableString() {
            skipSpaces();
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            return string();
        }

        public int integer() {
            skipSpaces();
            int start = pos;
            if (pos < text.length() && text.charAt(pos) == '-') {
                pos++;
            }
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            if (start == pos || (start == pos - 1 && text.charAt(start) == '-')) {
                throw error("位置 " + start + " 期望整数");
            }
            try {
                return Integer.parseInt(text.substring(start, pos));
            } catch (NumberFormatException ex) {
                throw error("整数溢出：" + text.substring(start, pos));
            }
        }

        public boolean bool() {
            skipSpaces();
            if (text.startsWith("true", pos)) {
                pos += 4;
                return true;
            }
            if (text.startsWith("false", pos)) {
                pos += 5;
                return false;
            }
            throw error("位置 " + pos + " 期望布尔值");
        }

        /** 原样取出一段值文本（不解析），用于把嵌套对象当字符串搬运。 */
        public String rawValue() {
            skipSpaces();
            int start = pos;
            scanValue();
            return text.substring(start, pos);
        }

        /** 丢弃一段值；用于忽略本端不认识的新字段。 */
        public void skipValue() {
            skipSpaces();
            scanValue();
        }

        private void scanValue() {
            skipSpaces();
            if (pos >= text.length()) {
                throw error("值意外结束");
            }
            char ch = text.charAt(pos);
            if (ch == '"') {
                string();
                return;
            }
            if (ch == '{' || ch == '[') {
                scanComposite();
                return;
            }
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                    break;
                }
                pos++;
            }
            if (start == pos) {
                throw error("位置 " + start + " 处是空值");
            }
        }

        /** 括号配平扫描；字符串内的括号不计入配平。 */
        private void scanComposite() {
            int depth = 0;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == '"') {
                    string();
                    continue;
                }
                if (c == '{' || c == '[') {
                    depth++;
                    pos++;
                    continue;
                }
                if (c == '}' || c == ']') {
                    depth--;
                    pos++;
                    if (depth == 0) {
                        return;
                    }
                    continue;
                }
                pos++;
            }
            throw error("结构未闭合");
        }

        private void skipSpaces() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private IllegalArgumentException error(String what) {
            return new IllegalArgumentException(label + " 语法错误（" + what + "）：" + text);
        }
    }
}
