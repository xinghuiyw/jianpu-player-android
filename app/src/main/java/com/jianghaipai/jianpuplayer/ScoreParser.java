package com.jianghaipai.jianpuplayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A deliberately small, editable text form of numbered notation. */
public final class ScoreParser {
    public static final class Event {
        public final double beat;
        public final double duration;
        public final List<Integer> keys;

        Event(double beat, double duration, List<Integer> keys) {
            this.beat = beat;
            this.duration = duration;
            this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
        }
    }

    private ScoreParser() {}

    public static List<Event> parse(String source) {
        if (source == null || source.trim().isEmpty()) throw new IllegalArgumentException("请输入简谱");
        List<Event> events = new ArrayList<>();
        double beat = 0;
        String[] tokens = source.replace('|', ' ').replace('｜', ' ').split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.isEmpty()) continue;
            if (token.equals("-")) {
                if (events.isEmpty()) throw new IllegalArgumentException("延长线 - 前面需要音符");
                Event last = events.remove(events.size() - 1);
                events.add(new Event(last.beat, last.duration + 1, last.keys));
                beat += 1;
                continue;
            }
            double duration = 1;
            int slash = token.lastIndexOf('/');
            int star = token.lastIndexOf('*');
            int split = Math.max(slash, star);
            if (split >= 0) {
                String number = token.substring(split + 1);
                double value;
                try { value = Double.parseDouble(number); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("时值无效：" + token); }
                if (value <= 0 || value > 64) throw new IllegalArgumentException("时值需大于 0 且不超过 64：" + token);
                duration = split == slash ? 1.0 / value : value;
                token = token.substring(0, split);
            }
            List<Integer> keys = new ArrayList<>();
            if (!token.equals("0")) {
                String notes = token;
                if (token.startsWith("[") && token.endsWith("]")) notes = token.substring(1, token.length() - 1);
                else if (token.startsWith("[") || token.endsWith("]")) throw new IllegalArgumentException("和弦括号不完整：" + token);
                for (int n = 0; n < notes.length(); n++) {
                    char c = notes.charAt(n);
                    if (c == '\'') {
                        if (keys.size() != 1 || n != notes.length() - 1 || keys.get(0) != 1)
                            throw new IllegalArgumentException("目前只支持高音 1'：" + token);
                        keys.set(0, 8);
                    } else if (c >= '1' && c <= '8') {
                        int key = c - '0';
                        if (!keys.contains(key)) keys.add(key);
                    } else throw new IllegalArgumentException("无法识别音符：" + token);
                }
                if (keys.isEmpty()) throw new IllegalArgumentException("空和弦：" + token);
            }
            events.add(new Event(beat, duration, keys));
            beat += duration;
        }
        if (events.isEmpty()) throw new IllegalArgumentException("请输入至少一个音符");
        return events;
    }
}
