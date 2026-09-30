package com.jianghaipai.jianpuplayer;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Small local library shared by the editor and the floating player. */
public final class SongLibrary {
    public static final class Song {
        public final String id;
        public final String name;
        public final String score;
        public final int bpm;

        Song(String id, String name, String score, int bpm) {
            this.id = id;
            this.name = name;
            this.score = score;
            this.bpm = bpm;
        }
    }

    private static final String SONGS = "songs_json";
    private static final String SELECTED = "selected_song_id";
    private static final String INITIALIZED = "songs_initialized";
    private static final String BUILTIN_IMPORTED = "builtin_song_imported";

    private SongLibrary() {}

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
    }

    private static void migrate(Context context) {
        SharedPreferences prefs = preferences(context);
        if (prefs.getBoolean(INITIALIZED, false)) return;
        SharedPreferences.Editor edit = prefs.edit();
        if (prefs.contains("score")) {
            String id = UUID.randomUUID().toString();
            JSONArray array = new JSONArray();
            array.put(toJson(new Song(id, "旧版曲谱", prefs.getString("score", ""), prefs.getInt("bpm", 100))));
            edit.putString(SONGS, array.toString()).putString(SELECTED, id);
        }
        edit.putBoolean(INITIALIZED, true).apply();
    }

    private static void importBuiltInSong(Context context) {
        SharedPreferences prefs = preferences(context);
        if (prefs.getBoolean(BUILTIN_IMPORTED, false)) return;
        try {
            JSONArray array = new JSONArray(prefs.getString(SONGS, "[]"));
            String name = "我曾爱过一个人（内置）";
            String selectedId = prefs.getString(SELECTED, null);
            boolean exists = false;
            for (int i = 0; i < array.length(); i++) {
                if (name.equals(array.getJSONObject(i).getString("name"))) exists = true;
            }
            if (!exists) {
                String score = readAsset(context, "builtin_song.txt").trim();
                ScoreParser.parse(score);
                Song song = new Song(UUID.randomUUID().toString(), name, score, 88);
                array.put(toJson(song));
                if (selectedId == null) selectedId = song.id;
            }
            SharedPreferences.Editor edit = prefs.edit()
                .putString(SONGS, array.toString()).putBoolean(BUILTIN_IMPORTED, true);
            if (selectedId != null) edit.putString(SELECTED, selectedId);
            edit.apply();
        } catch (IOException | JSONException error) {
            throw new IllegalStateException("内置曲谱载入失败", error);
        }
    }

    private static String readAsset(Context context, String name) throws IOException {
        try (InputStream input = context.getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static synchronized List<Song> list(Context context) {
        migrate(context);
        importBuiltInSong(context);
        List<Song> songs = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences(context).getString(SONGS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                songs.add(new Song(object.getString("id"), object.getString("name"),
                    object.getString("score"), object.getInt("bpm")));
            }
        } catch (JSONException error) { throw new IllegalStateException("本地曲库读取失败", error); }
        return songs;
    }

    public static synchronized Song selected(Context context) {
        List<Song> songs = list(context);
        String id = preferences(context).getString(SELECTED, null);
        for (Song song : songs) if (song.id.equals(id)) return song;
        return null;
    }

    public static synchronized Song select(Context context, String id) {
        for (Song song : list(context)) {
            if (song.id.equals(id)) {
                preferences(context).edit().putString(SELECTED, id).apply();
                return song;
            }
        }
        throw new IllegalArgumentException("曲目不存在");
    }

    public static synchronized Song saveNew(Context context, String name, String score, int bpm) {
        List<Song> songs = list(context);
        String title = validate(name, score, bpm);
        assertUnique(songs, title, null);
        Song song = new Song(UUID.randomUUID().toString(), title, score, bpm);
        songs.add(song);
        persist(context, songs, song.id);
        return song;
    }

    public static synchronized Song update(Context context, String id, String name, String score, int bpm) {
        List<Song> songs = list(context);
        String title = validate(name, score, bpm);
        assertUnique(songs, title, id);
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id.equals(id)) {
                Song updated = new Song(id, title, score, bpm);
                songs.set(i, updated);
                persist(context, songs, id);
                return updated;
            }
        }
        throw new IllegalArgumentException("当前曲目已不存在，请另存为新曲");
    }

    public static synchronized void delete(Context context, String id) {
        List<Song> songs = list(context);
        boolean removed = songs.removeIf(song -> song.id.equals(id));
        if (!removed) throw new IllegalArgumentException("曲目不存在");
        String selected = preferences(context).getString(SELECTED, null);
        String next = id.equals(selected) ? (songs.isEmpty() ? null : songs.get(0).id) : selected;
        persist(context, songs, next);
    }

    private static String validate(String name, String score, int bpm) {
        String title = name == null ? "" : name.trim();
        if (title.isEmpty()) throw new IllegalArgumentException("请输入歌曲名称");
        if (title.length() > 40) throw new IllegalArgumentException("歌曲名称不能超过 40 个字");
        if (bpm < 30 || bpm > 300) throw new IllegalArgumentException("速度须在 30–300 BPM 之间");
        ScoreParser.parse(score);
        return title;
    }

    private static void assertUnique(List<Song> songs, String title, String exceptId) {
        for (Song song : songs) {
            if (!song.id.equals(exceptId) && song.name.equalsIgnoreCase(title))
                throw new IllegalArgumentException("已有同名歌曲；请更改名称或更新该曲目");
        }
    }

    private static void persist(Context context, List<Song> songs, String selectedId) {
        JSONArray array = new JSONArray();
        for (Song song : songs) array.put(toJson(song));
        SharedPreferences.Editor edit = preferences(context).edit().putString(SONGS, array.toString());
        if (selectedId == null) edit.remove(SELECTED); else edit.putString(SELECTED, selectedId);
        edit.apply();
    }

    private static JSONObject toJson(Song song) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", song.id);
            object.put("name", song.name);
            object.put("score", song.score);
            object.put("bpm", song.bpm);
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        return object;
    }
}
