package io.github.tryrodave.jsonextended.obj;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits OBJ and MTL text into statements. A line ending in {@code \} continues on the next one. A {@code #} only
 * starts a comment at the start of a line (after spaces), so {@code map_Kd #texture0} keeps its texture variable.
 */
final class ObjLines {
    /** One statement: its keyword and arguments, and the line it starts on (1-based). */
    record Line(int number, String[] tokens) {
        String keyword() {
            return tokens[0];
        }

        int args() {
            return tokens.length - 1;
        }
    }

    private ObjLines() {}

    static List<Line> split(String text) {
        List<Line> lines = new ArrayList<>();
        String[] raw = text.split("\r\n|\r|\n", -1);
        StringBuilder joined = new StringBuilder();
        int start = 0;
        for (int i = 0; i < raw.length; i++) {
            String line = raw[i];
            if (joined.isEmpty())
                start = i + 1;
            String stripped = line.stripTrailing();
            if (stripped.endsWith("\\")) {
                joined.append(stripped, 0, stripped.length() - 1).append(' ');
                if (i < raw.length - 1)
                    continue;
            } else {
                joined.append(line);
            }
            String statement = joined.toString().strip();
            joined.setLength(0);
            if (statement.isEmpty() || statement.startsWith("#"))
                continue;
            lines.add(new Line(start, statement.split("\\s+")));
        }
        return lines;
    }

    static float number(String id, Line line, int index) {
        try {
            return Float.parseFloat(line.tokens()[index]);
        } catch (NumberFormatException e) {
            throw ObjException.at(id, line.number(), "'" + line.tokens()[index] + "' is not a number");
        }
    }
}
