package com.example.myapplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class Mood {

    public static final int CATEGORY_GOOD = 0;
    public static final int CATEGORY_NORMAL = 1;
    public static final int CATEGORY_BAD = 2;

    public final String id;
    public final String label;
    public final String emoji;
    public final int category;

    private Mood(String id, String label, String emoji, int category) {
        this.id = id;
        this.label = label;
        this.emoji = emoji;
        this.category = category;
    }

    private static final List<Mood> ALL;
    private static final Map<String, Mood> BY_ID;

    static {
        List<Mood> moods = new ArrayList<>();
        moods.add(new Mood("excited", "Excited", "😄", CATEGORY_GOOD));
        moods.add(new Mood("happy", "Happy", "😊", CATEGORY_GOOD));
        moods.add(new Mood("peaceful", "Peaceful", "😌", CATEGORY_GOOD));
        moods.add(new Mood("loved", "Loved", "🥰", CATEGORY_GOOD));
        moods.add(new Mood("neutral", "Neutral", "😐", CATEGORY_NORMAL));
        moods.add(new Mood("thinking", "Thinking", "🤔", CATEGORY_NORMAL));
        moods.add(new Mood("sad", "Sad", "😔", CATEGORY_BAD));
        moods.add(new Mood("crying", "Crying", "😢", CATEGORY_BAD));
        moods.add(new Mood("angry", "Angry", "😠", CATEGORY_BAD));
        moods.add(new Mood("anxious", "Anxious", "😰", CATEGORY_BAD));
        ALL = Collections.unmodifiableList(moods);

        Map<String, Mood> byId = new HashMap<>();
        for (Mood mood : ALL) {
            byId.put(mood.id, mood);
        }
        BY_ID = Collections.unmodifiableMap(byId);
    }

    public static List<Mood> all() {
        return ALL;
    }

    public static Mood findById(String id) {
        if (id == null) return null;
        return BY_ID.get(id);
    }
}
