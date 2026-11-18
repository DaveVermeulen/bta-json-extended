package io.github.tryrodave.jsonextended.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small strict JSON reader, so core needs no library. Objects become {@link LinkedHashMap} (key order kept,
 * which variant matching relies on), arrays {@link List}, numbers {@link Double}, plus {@link String},
 * {@link Boolean} and {@code null}.
 */
public final class Json {
    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json json = new Json(text);
        json.skipSpace();
        Object value = json.value();
        json.skipSpace();
        if (json.pos != text.length())
            throw json.error("unexpected text after the JSON value");
        return value;
    }

    private Object value() {
        if (pos >= text.length())
            throw error("unexpected end of file");
        char c = text.charAt(pos);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return word("true", Boolean.TRUE);
            case 'f': return word("false", Boolean.FALSE);
            case 'n': return word("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9'))
                    return number();
                throw error("unexpected '" + c + "'");
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipSpace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipSpace();
            if (peek() != '"')
                throw error("expected a key in quotes");
            String key = string();
            skipSpace();
            expect(':');
            skipSpace();
            if (map.put(key, value()) != null)
                throw error("duplicate key '" + key + "'");
            skipSpace();
            if (peek() == ',') {
                pos++;
                continue;
            }
            expect('}');
            return map;
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipSpace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipSpace();
            list.add(value());
            skipSpace();
            if (peek() == ',') {
                pos++;
                continue;
            }
            expect(']');
            return list;
        }
    }

    private String string() {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length())
                throw error("unterminated string");
            char c = text.charAt(pos++);
            if (c == '"')
                return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length())
                throw error("unterminated string");
            char e = text.charAt(pos++);
            switch (e) {
                case '"': case '\\': case '/': sb.append(e); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (pos + 4 > text.length())
                        throw error("bad \\u escape");
                    try {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("bad \\u escape");
                    }
                    pos += 4;
                    break;
                default: throw error("bad escape \\" + e);
            }
        }
    }

    private Double number() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0)
            pos++;
        try {
            return Double.valueOf(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("bad number '" + text.substring(start, pos) + "'");
        }
    }

    private Object word(String word, Object value) {
        if (!text.startsWith(word, pos))
            throw error("unexpected '" + text.charAt(pos) + "'");
        pos += word.length();
        return value;
    }

    private char peek() {
        if (pos >= text.length())
            throw error("unexpected end of file");
        return text.charAt(pos);
    }

    private void expect(char c) {
        if (peek() != c)
            throw error("expected '" + c + "'");
        pos++;
    }

    private void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos)))
            pos++;
    }

    private IllegalArgumentException error(String message) {
        int line = 1, column = 1;
        for (int i = 0; i < pos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new IllegalArgumentException("invalid JSON at line " + line + ", column " + column + ": " + message);
    }
}
