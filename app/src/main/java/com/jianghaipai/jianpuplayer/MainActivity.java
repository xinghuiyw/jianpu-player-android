package com.jianghaipai.jianpuplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public final class MainActivity extends Activity {
    static final String PREFS = "player";
    static final String DEFAULT_SCORE = "1 2 3 4 | 5 5 5 - | 6/2 5/2 4 3 | 2 2 1 -";
    private static final String AI_PROMPT =
        "我会上传一张或多张按顺序排列的数字简谱图片。请逐行、逐小节转写整首曲子，"
        + "转换成安卓软件可以直接粘贴的纯文本，不要把歌词、标题里的数字当音符，也不要猜测看不清的地方。\n\n"
        + "软件格式：每个音符用空格分开，小节之间用 |。1～7 对应哆到西，8 表示高音哆，0 表示休止符。"
        + "默认每个数字占一拍；有一条减时线写 /2（半拍），两条减时线写 /4（四分之一拍）；"
        + "延长一拍就在原音符后单独写 -，两拍可写两个 -；同时按的音写成 [135]。"
        + "请按原谱标注的四分音符速度告诉我 BPM。\n\n"
        + "这款软件只有 1～8 八个按键，低音点请暂时按同数字处理，但要在结果外明确列出哪些音被折叠；"
        + "装饰小音符可以省略，但要说明；遇到转调，也要在结果外标明位置。"
        + "若图片里的音符或时值看不清，请先指出位置并让我补图，不要编造。\n\n"
        + "请把可粘贴的曲谱放在一个独立代码块中，代码块里面只放软件支持的音符和节奏文本；"
        + "曲名、BPM、注意事项放在代码块外。输出前请检查每个音符都以空格分开，"
        + "不要在代码块里放中文、序号或解释。";
    private EditText nameInput;
    private EditText scoreInput;
    private EditText bpmInput;
    private TextView currentSongLabel;
    private TextView status;
    private String currentSongId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(248, 249, 251));
        scroll.addView(root);
        addText(root, "简谱演奏器 · 仅供学习交流", 25, true);
        addText(root, "本软件仅供学习交流，下载后请24小时内删除。", 14, false);
        addText(root, "保存多首文字简谱，在游戏里的悬浮窗选曲并演奏。", 15, false);
        currentSongLabel = addText(root, "当前：未保存的曲谱", 15, true);
        addText(root, "歌曲名称", 18, true);
        nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("例如：我曾爱过一个人");
        root.addView(nameInput);
        addText(root, "简谱", 19, true);
        scoreInput = new EditText(this);
        scoreInput.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("score", DEFAULT_SCORE));
        scoreInput.setMinLines(7);
        scoreInput.setGravity(48);
        scoreInput.setTextSize(18);
        scoreInput.setBackgroundColor(Color.WHITE);
        scoreInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(scoreInput, new LinearLayout.LayoutParams(-1, dp(190)));
        addText(root, "格式：1–7 是一拍；8 或 1' 是高音哆；0 是休止。/2 是半拍，*2 是两拍，- 延长一拍，[135] 为同时按。音符之间用空格分隔。", 14, false);
        addText(root, "速度（每分钟拍数）", 18, true);
        bpmInput = new EditText(this);
        bpmInput.setInputType(2);
        bpmInput.setText(String.valueOf(getSharedPreferences(PREFS, MODE_PRIVATE).getInt("bpm", 100)));
        root.addView(bpmInput);
        Button saveNew = button("保存为新曲");
        root.addView(saveNew);
        saveNew.setOnClickListener(v -> saveSong(false));
        Button update = button("更新当前曲目");
        root.addView(update);
        update.setOnClickListener(v -> saveSong(true));
        Button choose = button("载入已保存曲目");
        root.addView(choose);
        choose.setOnClickListener(v -> chooseSong());
        Button delete = button("删除当前曲目");
        root.addView(delete);
        delete.setOnClickListener(v -> confirmDelete());
        Button enable = button("开启无障碍点击服务");
        root.addView(enable);
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button panel = button("显示悬浮控制条");
        root.addView(panel);
        panel.setOnClickListener(v -> {
            SongLibrary.Song selected = SongLibrary.selected(this);
            if (selected == null) { toast("请先保存一首歌曲"); return; }
            if (!selected.id.equals(currentSongId) || !isFormSameAs(selected)) {
                toast("当前修改尚未保存，请先更新曲目或载入已保存曲目"); return;
            }
            PlayerAccessibilityService service = PlayerAccessibilityService.getInstance();
            if (service == null) { toast("请先在系统设置中开启简谱演奏器点击服务"); return; }
            service.showPanel();
            toast("已显示。游戏内可点“选曲”切换已保存的歌曲。");
        });
        status = addText(root, "", 14, false);
        addText(root, "首次使用：输入曲名和简谱并保存 → 开启无障碍服务 → 显示悬浮控制条 → 在游戏里调位置、试按、选曲和开始。切换屏幕方向后需重新校准。", 14, false);
        addText(root, "如何把简谱图片转成可用文字（仅供学习交流）", 19, true);
        addText(root, "可以把简谱图片发给免费 AI，让它按下面的提示词转换。复制 AI 输出的曲谱代码块，粘贴到上方“简谱”框，填入曲名和 BPM 后保存。转换结果请与原图核对。", 14, false);
        TextView prompt = addText(root, AI_PROMPT, 14, false);
        prompt.setTextIsSelectable(true);
        prompt.setBackgroundColor(Color.WHITE);
        prompt.setPadding(dp(12), dp(12), dp(12), dp(12));
        Button copyPrompt = button("复制 AI 转谱提示词");
        root.addView(copyPrompt);
        copyPrompt.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("简谱转换提示词", AI_PROMPT));
            toast("提示词已复制；仅供学习交流");
        });
        addText(root, "内置示例：《我曾爱过一个人（内置）》，88 BPM。仅供学习交流，下载后请24小时内删除。", 14, false);
        setContentView(scroll);
        SongLibrary.Song selected = SongLibrary.selected(this);
        if (selected != null) loadSong(selected);
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) status.setText(PlayerAccessibilityService.getInstance() == null ? "状态：无障碍服务未开启" : "状态：无障碍服务已开启");
        if (nameInput != null) {
            SongLibrary.Song selected = SongLibrary.selected(this);
            if (selected != null && !selected.id.equals(currentSongId)) loadSong(selected);
        }
    }

    private void saveSong(boolean update) {
        try {
            int bpm = Integer.parseInt(bpmInput.getText().toString().trim());
            String score = scoreInput.getText().toString();
            List<ScoreParser.Event> events = ScoreParser.parse(score);
            if (update && currentSongId == null) throw new IllegalArgumentException("请先载入曲目，或使用“保存为新曲”");
            SongLibrary.Song song = update
                ? SongLibrary.update(this, currentSongId, nameInput.getText().toString(), score, bpm)
                : SongLibrary.saveNew(this, nameInput.getText().toString(), score, bpm);
            loadSong(song);
            toast("已保存《" + song.name + "》，共 " + events.size() + " 个节奏事件");
        } catch (Exception e) { toast(e.getMessage()); }
    }

    private void chooseSong() {
        List<SongLibrary.Song> songs = SongLibrary.list(this);
        if (songs.isEmpty()) { toast("还没有保存的歌曲"); return; }
        String[] labels = new String[songs.size()];
        for (int i = 0; i < songs.size(); i++) labels[i] = songs.get(i).name + "  ·  " + songs.get(i).bpm + " BPM";
        new AlertDialog.Builder(this).setTitle("选择歌曲 · 仅供学习交流").setItems(labels, (dialog, index) -> {
            SongLibrary.Song selected = SongLibrary.select(this, songs.get(index).id);
            loadSong(selected);
        }).setNegativeButton("取消", null).show();
    }

    private void confirmDelete() {
        if (currentSongId == null) { toast("当前没有已保存的曲目"); return; }
        String id = currentSongId;
        String name = nameInput.getText().toString();
        new AlertDialog.Builder(this).setTitle("删除《" + name + "》？ · 仅供学习交流")
            .setMessage("删除后无法从应用内恢复。")
            .setPositiveButton("删除", (dialog, which) -> {
                SongLibrary.delete(this, id);
                SongLibrary.Song next = SongLibrary.selected(this);
                if (next != null) loadSong(next);
                else {
                    currentSongId = null;
                    currentSongLabel.setText("当前：未保存的曲谱");
                    nameInput.setText("");
                    scoreInput.setText("");
                }
                toast("已删除");
            }).setNegativeButton("取消", null).show();
    }

    private void loadSong(SongLibrary.Song song) {
        currentSongId = song.id;
        currentSongLabel.setText("当前：《" + song.name + "》");
        nameInput.setText(song.name);
        scoreInput.setText(song.score);
        bpmInput.setText(String.valueOf(song.bpm));
    }

    private boolean isFormSameAs(SongLibrary.Song song) {
        try {
            return song.name.equals(nameInput.getText().toString().trim())
                && song.score.equals(scoreInput.getText().toString())
                && song.bpm == Integer.parseInt(bpmInput.getText().toString().trim());
        } catch (NumberFormatException error) { return false; }
    }

    private TextView addText(LinearLayout root, String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(30, 36, 44));
        if (bold) view.setTypeface(null, 1);
        view.setPadding(0, dp(10), 0, dp(8));
        root.addView(view);
        return view;
    }

    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); return b; }
    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
