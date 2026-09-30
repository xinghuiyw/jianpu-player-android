package com.jianghaipai.jianpuplayer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Collections;

public final class PlayerAccessibilityService extends AccessibilityService {
    private static PlayerAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private LinearLayout panel;
    private FrameLayout calibrator;
    private FrameLayout songPicker;
    private Button playButton;
    private TextView songLabel;
    private int testKey = 1;
    private long generation = 0;
    private boolean playing = false;
    private List<ScoreParser.Event> events;
    private int bpm;

    static PlayerAccessibilityService getInstance() { return instance; }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { stop(); }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (panel != null) panel.post(this::keepPanelOnScreen);
    }
    @Override public void onDestroy() {
        stop();
        removeSongPicker();
        removeCalibrator();
        hidePanel();
        instance = null;
        super.onDestroy();
    }

    public void showPanel() {
        if (panel != null) { refreshSongLabel(); return; }
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(3), dp(3), dp(3), dp(3));
        panel.setBackground(rounded(Color.argb(95, 27, 32, 41), dp(10)));
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView grip = new TextView(this);
        grip.setText("⠿");
        grip.setTextSize(22);
        grip.setTextColor(Color.WHITE);
        grip.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
        grip.setGravity(Gravity.CENTER);
        grip.setContentDescription("拖动悬浮窗");
        header.addView(grip, new LinearLayout.LayoutParams(dp(32), dp(36)));
        songLabel = new TextView(this);
        songLabel.setTextColor(Color.WHITE);
        songLabel.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
        songLabel.setTextSize(14);
        songLabel.setSingleLine(true);
        songLabel.setEllipsize(TextUtils.TruncateAt.END);
        songLabel.setMaxWidth(dp(190));
        songLabel.setPadding(dp(3), 0, dp(3), 0);
        Button select = panelButton("选曲");
        header.addView(songLabel, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(select);
        panel.addView(header);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        playButton = panelButton("开始");
        Button calibrate = panelButton("调位置");
        Button test = panelButton("试按");
        Button close = panelButton("×");
        controls.addView(playButton);
        controls.addView(calibrate);
        controls.addView(test);
        controls.addView(close);
        panel.addView(controls);
        TextView panelNotice = new TextView(this);
        panelNotice.setText("仅供学习交流");
        panelNotice.setTextSize(10);
        panelNotice.setTextColor(Color.WHITE);
        panelNotice.setGravity(Gravity.RIGHT);
        panelNotice.setPadding(0, 0, dp(6), 0);
        panel.addView(panelNotice);
        View.OnTouchListener drag = panelDragListener();
        grip.setOnTouchListener(drag);
        songLabel.setOnTouchListener(drag);
        refreshSongLabel();
        select.setOnClickListener(v -> showSongPicker());
        playButton.setOnClickListener(v -> { if (playing) stop(); else start(); });
        calibrate.setOnClickListener(v -> showCalibrator());
        test.setOnClickListener(v -> testNextKey());
        close.setOnClickListener(v -> { stop(); removeSongPicker(); removeCalibrator(); hidePanel(); });
        Point screen = screenSize();
        int width = Math.min(dp(340), screen.x - dp(24));
        WindowManager.LayoutParams params = overlayParams(width, -2);
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        boolean sameScreen = preferences.getInt("panelW", 0) == screen.x
            && preferences.getInt("panelH", 0) == screen.y;
        params.x = sameScreen ? preferences.getInt("panelX", 0) : Math.max(0, screen.x - width - dp(12));
        params.y = sameScreen ? preferences.getInt("panelY", dp(60)) : dp(60);
        windows.addView(panel, params);
        panel.post(this::keepPanelOnScreen);
    }

    private View.OnTouchListener panelDragListener() {
        return new View.OnTouchListener() {
            float touchX, touchY;
            int startX, startY;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (panel == null) return false;
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) panel.getLayoutParams();
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    touchX = event.getRawX(); touchY = event.getRawY();
                    startX = params.x; startY = params.y;
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP) {
                    Point screen = screenSize();
                    params.x = Math.max(0, Math.min(screen.x - panel.getWidth(),
                        Math.round(startX + event.getRawX() - touchX)));
                    params.y = Math.max(0, Math.min(screen.y - panel.getHeight(),
                        Math.round(startY + event.getRawY() - touchY)));
                    windows.updateViewLayout(panel, params);
                    if (event.getAction() == MotionEvent.ACTION_UP) savePanelPosition(params);
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    savePanelPosition(params);
                    return true;
                }
                return false;
            }
        };
    }

    private void keepPanelOnScreen() {
        if (panel == null) return;
        Point screen = screenSize();
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) panel.getLayoutParams();
        params.width = Math.min(dp(340), screen.x - dp(24));
        params.x = Math.max(0, Math.min(screen.x - params.width, params.x));
        params.y = Math.max(0, Math.min(screen.y - panel.getHeight(), params.y));
        windows.updateViewLayout(panel, params);
    }

    private void savePanelPosition(WindowManager.LayoutParams params) {
        Point screen = screenSize();
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
            .putInt("panelW", screen.x).putInt("panelH", screen.y)
            .putInt("panelX", params.x).putInt("panelY", params.y).apply();
    }

    private void hidePanel() {
        if (panel != null) { windows.removeView(panel); panel = null; playButton = null; songLabel = null; }
    }

    private void refreshSongLabel() {
        if (songLabel == null) return;
        SongLibrary.Song song = SongLibrary.selected(this);
        songLabel.setText(song == null ? "未选曲" : song.name + "  " + song.bpm + " BPM");
    }

    private void showSongPicker() {
        stop();
        if (songPicker != null) return;
        List<SongLibrary.Song> songs = SongLibrary.list(this);
        if (songs.isEmpty()) { toast("还没有保存的歌曲，请先在主界面保存"); return; }
        songPicker = new FrameLayout(this);
        songPicker.setBackgroundColor(Color.argb(160, 0, 0, 0));
        songPicker.setOnClickListener(v -> removeSongPicker());
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(rounded(Color.WHITE, dp(14)));
        card.setOnClickListener(v -> {});
        TextView title = new TextView(this);
        title.setText("选择歌曲 · 仅供学习交流");
        title.setTextSize(19);
        title.setTextColor(Color.rgb(25, 32, 40));
        title.setPadding(dp(5), dp(5), dp(5), dp(10));
        card.addView(title);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        for (SongLibrary.Song song : songs) {
            Button item = smallButton(song.name + "  ·  " + song.bpm + " BPM");
            list.addView(item, new LinearLayout.LayoutParams(-1, dp(48)));
            item.setOnClickListener(v -> {
                SongLibrary.select(this, song.id);
                removeSongPicker();
                refreshSongLabel();
                toast("已选《" + song.name + "》");
            });
        }
        card.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button cancel = smallButton("取消");
        card.addView(cancel, new LinearLayout.LayoutParams(-1, dp(45)));
        cancel.setOnClickListener(v -> removeSongPicker());
        Point screen = screenSize();
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
            Math.min(dp(380), screen.x - dp(24)), Math.min(dp(420), screen.y - dp(24)), Gravity.CENTER);
        songPicker.addView(card, cardParams);
        windows.addView(songPicker, overlayParams(-1, -1));
    }

    private void removeSongPicker() {
        if (songPicker != null) { windows.removeView(songPicker); songPicker = null; }
    }

    private void showCalibrator() {
        stop();
        removeSongPicker();
        if (calibrator != null) return;
        calibrator = new FrameLayout(this);
        calibrator.setBackgroundColor(Color.argb(70, 10, 20, 30));
        TextView instruction = new TextView(this);
        instruction.setText("仅供学习交流｜拖动数字圆点到琴键中心，完成后点保存");
        instruction.setTextColor(Color.WHITE);
        instruction.setTextSize(16);
        instruction.setBackgroundColor(Color.argb(210, 15, 20, 30));
        instruction.setPadding(dp(12), dp(8), dp(12), dp(8));
        FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        calibrator.addView(instruction, top);
        final int markerSize = dp(46);
        TextView[] markers = new TextView[8];
        for (int key = 1; key <= 8; key++) {
            TextView marker = new TextView(this);
            markers[key - 1] = marker;
            marker.setText(key == 8 ? "高1" : String.valueOf(key));
            marker.setTextColor(Color.WHITE);
            marker.setTextSize(17);
            marker.setTypeface(null, 1);
            marker.setGravity(Gravity.CENTER);
            marker.setBackground(rounded(Color.argb(235, 30, 109, 222), markerSize));
            calibrator.addView(marker, new FrameLayout.LayoutParams(markerSize, markerSize));
            marker.setOnTouchListener(new View.OnTouchListener() {
                float startX, startY, touchX, touchY;
                @Override public boolean onTouch(View v, MotionEvent e) {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        startX = v.getX(); startY = v.getY(); touchX = e.getRawX(); touchY = e.getRawY(); return true;
                    }
                    if (e.getAction() == MotionEvent.ACTION_MOVE || e.getAction() == MotionEvent.ACTION_UP) {
                        float x = Math.max(0, Math.min(calibrator.getWidth() - markerSize, startX + e.getRawX() - touchX));
                        float y = Math.max(0, Math.min(calibrator.getHeight() - markerSize, startY + e.getRawY() - touchY));
                        v.setX(x); v.setY(y);
                        return true;
                    }
                    return false;
                }
            });
        }
        Button done = smallButton("保存位置");
        FrameLayout.LayoutParams bottom = new FrameLayout.LayoutParams(-2, dp(50), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottom.bottomMargin = dp(36);
        calibrator.addView(done, bottom);
        done.setOnClickListener(v -> { saveCalibration(markers); removeCalibrator(); toast("位置已保存；可用“试按”逐键检查"); });
        WindowManager.LayoutParams params = overlayParams(-1, -1);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        windows.addView(calibrator, params);
        calibrator.post(() -> {
            if (calibrator == null) return;
            int[] origin = new int[2];
            calibrator.getLocationOnScreen(origin);
            SharedPreferences preferences = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
            Point screen = screenSize();
            boolean sameScreen = preferences.getBoolean("calibrated", false)
                && preferences.getInt("screenW", 0) == screen.x
                && preferences.getInt("screenH", 0) == screen.y;
            for (int i = 0; i < markers.length; i++) {
                float x = sameScreen ? preferences.getFloat("px" + (i + 1), 0) - origin[0]
                    : (i + .5f) * calibrator.getWidth() / 8f;
                float y = sameScreen ? preferences.getFloat("py" + (i + 1), 0) - origin[1]
                    : calibrator.getHeight() * .72f;
                markers[i].setX(Math.max(0, Math.min(calibrator.getWidth() - markerSize, x - markerSize / 2f)));
                markers[i].setY(Math.max(0, Math.min(calibrator.getHeight() - markerSize, y - markerSize / 2f)));
            }
        });
    }

    private void saveCalibration(TextView[] markers) {
        Point size = screenSize();
        SharedPreferences.Editor edit = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit();
        edit.putInt("screenW", size.x).putInt("screenH", size.y).putBoolean("calibrated", true);
        for (int i = 0; i < markers.length; i++) {
            int[] location = new int[2];
            markers[i].getLocationOnScreen(location);
            edit.putFloat("px" + (i + 1), location[0] + markers[i].getWidth() / 2f);
            edit.putFloat("py" + (i + 1), location[1] + markers[i].getHeight() / 2f);
        }
        edit.apply();
    }

    private boolean hasCalibration() {
        Point size = screenSize();
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        return preferences.getBoolean("calibrated", false)
            && preferences.getInt("screenW", 0) == size.x
            && preferences.getInt("screenH", 0) == size.y;
    }

    private void testNextKey() {
        if (playing) { toast("请先停止演奏再试按"); return; }
        if (!hasCalibration()) { toast("请先在当前屏幕方向调位置并保存"); return; }
        int key = testKey;
        tapKeys(Collections.singletonList(key));
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        toast("试按 " + (key == 8 ? "高1" : key) + "，坐标 ("
            + Math.round(preferences.getFloat("px" + key, 0)) + ", "
            + Math.round(preferences.getFloat("py" + key, 0)) + ")");
        testKey = key == 8 ? 1 : key + 1;
    }

    private void removeCalibrator() {
        if (calibrator != null) { windows.removeView(calibrator); calibrator = null; }
    }

    private void start() {
        try {
            SongLibrary.Song song = SongLibrary.selected(this);
            if (song == null) throw new IllegalArgumentException("请先在主界面保存歌曲");
            events = ScoreParser.parse(song.score);
            bpm = song.bpm;
            if (bpm < 30 || bpm > 300) throw new IllegalArgumentException("速度须在 30–300 BPM 之间");
            if (!hasCalibration()) throw new IllegalArgumentException("请先在当前屏幕方向调位置并保存");
            refreshSongLabel();
            removeCalibrator();
            playing = true;
            if (playButton != null) playButton.setText("停止");
            long token = ++generation;
            long origin = SystemClock.uptimeMillis() + 1500;
            schedule(0, token, origin);
            toast("1.5 秒后开始演奏");
        } catch (Exception e) { toast(e.getMessage()); stop(); }
    }

    private void schedule(int index, long token, long origin) {
        if (!playing || token != generation) return;
        if (index >= events.size()) { stop(); toast("演奏完成"); return; }
        ScoreParser.Event event = events.get(index);
        long when = origin + Math.round(event.beat * 60000.0 / bpm);
        handler.postAtTime(() -> {
            if (!playing || token != generation) return;
            if (!event.keys.isEmpty()) tapKeys(event.keys);
            schedule(index + 1, token, origin);
        }, when);
    }

    private void tapKeys(List<Integer> keys) {
        SharedPreferences preferences = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        GestureDescription.Builder gesture = new GestureDescription.Builder();
        for (int key : keys) {
            Path path = new Path();
            path.moveTo(preferences.getFloat("px" + key, 0), preferences.getFloat("py" + key, 0));
            gesture.addStroke(new GestureDescription.StrokeDescription(path, 0, 45));
        }
        dispatchGesture(gesture.build(), null, null);
    }

    private void stop() {
        playing = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        if (playButton != null) playButton.setText("开始");
    }

    private Point screenSize() {
        Point point = new Point();
        windows.getDefaultDisplay().getRealSize(point);
        return point;
    }

    private WindowManager.LayoutParams overlayParams(int width, int height) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        return params;
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private Button panelButton(String label) {
        Button button = smallButton(label);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(Color.argb(120, 35, 45, 60), dp(8)));
        button.setStateListAnimator(null);
        return button;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
