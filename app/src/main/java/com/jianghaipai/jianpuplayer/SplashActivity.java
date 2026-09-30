package com.jianghaipai.jianpuplayer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SplashActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(247, 249, 252));
        TextView title = text("简谱演奏器", 28, true);
        root.addView(title);
        TextView notice = text("本软件仅供学习交流，下载后请24小时内删除。", 19, true);
        notice.setPadding(0, dp(28), 0, dp(28));
        root.addView(notice);
        TextView description = text("仅供学习交流 · 请自行核对曲谱与实际演奏效果", 14, false);
        root.addView(description);
        Button enter = new Button(this);
        enter.setText("进入软件");
        enter.setAllCaps(false);
        root.addView(enter, new LinearLayout.LayoutParams(-1, dp(52)));
        enter.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        setContentView(root);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(25, 35, 50));
        view.setGravity(Gravity.CENTER);
        if (bold) view.setTypeface(null, 1);
        return view;
    }

    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
}
