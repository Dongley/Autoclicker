package com.dongle.tabautoclicker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private TextView position;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 21, 30));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(38), dp(24), dp(24));
        scroll.addView(root);

        root.addView(label("탭 오토클리커", 27, Color.WHITE, true));
        addGap(root, 8);
        root.addView(label("갤럭시 탭 · Android 8.0 이상", 14, Color.rgb(160,177,199), false));
        addGap(root, 26);

        root.addView(label("반복 동작", 19, Color.WHITE, true));
        addGap(root, 12);
        addInfo(root, "자동 클릭", "10초 동안 1초 간격");
        addInfo(root, "꾹 누르기", "1초");
        addInfo(root, "반복 횟수", "무한 반복 · 정지 버튼으로 종료");
        addGap(root, 26);

        root.addView(label("설정 상태", 19, Color.WHITE, true));
        addGap(root, 10);
        status = label("접근성 서비스 확인 중…", 16, Color.rgb(255,202,118), false);
        root.addView(status);
        addGap(root, 10);
        position = label("클릭 위치: 아직 선택되지 않음", 14, Color.rgb(164,183,203), false);
        root.addView(position);
        addGap(root, 22);

        Button accessibility = button("① 접근성 서비스 켜기");
        root.addView(accessibility);
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addGap(root, 10);

        Button showFloat = button("② 화면 위에 조작 버튼 표시");
        root.addView(showFloat);
        showFloat.setOnClickListener(v -> {
            AutoClickService service = AutoClickService.getInstance();
            if (service != null) service.showController();
            else startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        addGap(root, 20);

        root.addView(label("사용 방법", 18, Color.WHITE, true));
        addGap(root, 8);
        root.addView(label("1. 접근성 설정에서 ‘탭 오토클리커 접근성 서비스’를 켜세요.\n"
                + "2. 원하는 앱으로 이동하세요.\n"
                + "3. 떠 있는 버튼에서 ‘위치’를 누르고 화면의 한 지점을 터치하세요.\n"
                + "4. ‘시작’을 누르면 10초 클릭 → 1초 꾹 누르기가 반복됩니다.\n"
                + "5. 멈출 때는 떠 있는 ‘정지’ 버튼을 누르세요.",
                15, Color.rgb(188,204,221), false));
        addGap(root, 18);
        root.addView(label("이 앱은 화면 내용을 읽거나 인터넷에 연결하지 않습니다. 화면 제어를 위해 접근성 권한만 사용합니다.",
                12, Color.rgb(132,151,173), false));
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshState();
    }

    private void refreshState() {
        AutoClickService service = AutoClickService.getInstance();
        if (service == null) {
            status.setText("● 접근성 서비스 꺼짐");
            status.setTextColor(Color.rgb(255,170,122));
        } else {
            status.setText("● 접근성 서비스 켜짐 · 떠 있는 버튼 사용 가능");
            status.setTextColor(Color.rgb(129,228,171));
        }
        int x = getSharedPreferences("autoclick_settings", MODE_PRIVATE).getInt("clickX", -1);
        int y = getSharedPreferences("autoclick_settings", MODE_PRIVATE).getInt("clickY", -1);
        position.setText(x >= 0 && y >= 0 ? "클릭 위치: (" + x + ", " + y + ")" : "클릭 위치: 아직 선택되지 않음");
    }

    private void addInfo(LinearLayout root, String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(30,40,54));
        bg.setCornerRadius(dp(12));
        row.setBackground(bg);
        row.addView(label(title, 13, Color.rgb(153,176,206), false));
        addGap(row, 5);
        row.addView(label(value, 17, Color.WHITE, true));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        addGap(root, 8);
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setLineSpacing(dp(3), 1f);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(37,111,177));
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        b.setMinHeight(dp(54));
        return b;
    }

    private void addGap(LinearLayout l, int d) {
        View spacer = new View(this);
        l.addView(spacer, new LinearLayout.LayoutParams(1, dp(d)));
    }

    private int dp(float d) { return Math.round(d * getResources().getDisplayMetrics().density); }
}
