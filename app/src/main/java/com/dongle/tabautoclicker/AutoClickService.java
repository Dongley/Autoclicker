package com.dongle.tabautoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class AutoClickService extends AccessibilityService {
    private static final long CLICK_PHASE_MS = 3_500L;
    private static final long WAIT_DURATION_MS = 6_000L;
    private static final long FINAL_WAIT_MS = 3_000L;
    private static final long CLICK_INTERVAL_MS = 100L;
    private static final long HOLD_DURATION_MS = 1_500L;
    private static final long TAP_DURATION_MS = 40L;

    private static AutoClickService instance;
    public static AutoClickService getInstance() { return instance; }

    private WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private LinearLayout controller;
    private WindowManager.LayoutParams controllerParams;
    private FrameLayout selector;
    private TextView marker;
    private Button startStop;
    private boolean running = false;
    private int sequenceId = 0;
    private long cycleStartUptime;
    private int clickX = -1;
    private int clickY = -1;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        preferences = getSharedPreferences("autoclick_settings", MODE_PRIVATE);
        clickX = preferences.getInt("clickX", -1);
        clickY = preferences.getInt("clickY", -1);
        showController();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // No window or text content is inspected.
    }

    @Override public void onInterrupt() { stopLoop(); }

    @Override public boolean onUnbind(android.content.Intent intent) {
        stopLoop();
        removeAllViews();
        instance = null;
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        stopLoop();
        removeAllViews();
        instance = null;
        super.onDestroy();
    }

    public void showController() {
        if (windowManager == null) return;
        if (controller != null) {
            Toast.makeText(this, "떠 있는 조작 버튼이 표시되어 있어요", Toast.LENGTH_SHORT).show();
            return;
        }
        controller = new LinearLayout(this);
        controller.setOrientation(LinearLayout.HORIZONTAL);
        controller.setGravity(Gravity.CENTER_VERTICAL);
        controller.setPadding(dp(8), dp(5), dp(8), dp(5));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(28,38,53));
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), Color.rgb(95,124,154));
        controller.setBackground(background);
        controller.setElevation(dp(8));

        TextView handle = new TextView(this);
        handle.setText("☷");
        handle.setTextSize(23);
        handle.setGravity(Gravity.CENTER);
        handle.setTextColor(Color.WHITE);
        controller.addView(handle, new LinearLayout.LayoutParams(dp(40), dp(48)));
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX, downRawY;
            private int initialX, initialY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (controllerParams == null) return true;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        initialX = controllerParams.x;
                        initialY = controllerParams.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        controllerParams.x = Math.max(0, initialX + Math.round(event.getRawX() - downRawX));
                        controllerParams.y = Math.max(0, initialY + Math.round(event.getRawY() - downRawY));
                        try { windowManager.updateViewLayout(controller, controllerParams); }
                        catch (Exception ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default: return true;
                }
            }
        });

        Button choose = smallButton("위치", Color.rgb(62,101,148));
        choose.setOnClickListener(v -> choosePoint());
        controller.addView(choose);

        startStop = smallButton(running ? "정지" : "시작", Color.rgb(33,130,96));
        startStop.setOnClickListener(v -> {
            if (running) stopLoop(); else startLoop();
        });
        controller.addView(startStop);

        Button hide = smallButton("숨김", Color.rgb(92,98,114));
        hide.setOnClickListener(v -> {
            if (running) stopLoop();
            removeMarker();
            removeController();
            Toast.makeText(this, "앱을 다시 열면 버튼을 표시할 수 있어요", Toast.LENGTH_LONG).show();
        });
        controller.addView(hide);

        controllerParams = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        controllerParams.gravity = Gravity.TOP | Gravity.LEFT;
        controllerParams.x = dp(15);
        controllerParams.y = dp(130);
        try { windowManager.addView(controller, controllerParams); }
        catch (Exception e) {
            controller = null;
            controllerParams = null;
            Toast.makeText(this, "떠 있는 버튼을 표시하지 못했어요", Toast.LENGTH_SHORT).show();
        }
        showMarker();
    }

    private Button smallButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(42));
        b.setMinimumHeight(dp(42));
        b.setPadding(dp(11), 0, dp(11), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(42));
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void choosePoint() {
        if (windowManager == null || selector != null) return;
        stopLoop();
        removeMarker();
        selector = new FrameLayout(this);
        selector.setBackgroundColor(Color.argb(86, 0, 0, 0));
        TextView prompt = new TextView(this);
        prompt.setText("클릭할 화면 위치를 터치하세요");
        prompt.setTextColor(Color.WHITE);
        prompt.setTextSize(19);
        prompt.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        prompt.setGravity(Gravity.CENTER);
        prompt.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable promptBg = new GradientDrawable();
        promptBg.setColor(Color.rgb(31,47,67));
        promptBg.setCornerRadius(dp(12));
        prompt.setBackground(promptBg);
        FrameLayout.LayoutParams promptLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        promptLp.topMargin = dp(100);
        selector.addView(prompt, promptLp);

        selector.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                clickX = Math.round(event.getRawX());
                clickY = Math.round(event.getRawY());
                preferences.edit().putInt("clickX", clickX).putInt("clickY", clickY).apply();
                removeSelector();
                showMarker();
                Toast.makeText(this, "위치 저장: " + clickX + ", " + clickY, Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        WindowManager.LayoutParams selectorParams = overlayParams(-1, -1,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        selectorParams.gravity = Gravity.TOP | Gravity.LEFT;
        try { windowManager.addView(selector, selectorParams); }
        catch (Exception e) {
            selector = null;
            Toast.makeText(this, "위치 선택 창을 열지 못했어요", Toast.LENGTH_SHORT).show();
        }
    }

    private void showMarker() {
        removeMarker();
        if (windowManager == null || clickX < 0 || clickY < 0) return;
        marker = new TextView(this);
        marker.setText("+");
        marker.setTextSize(23);
        marker.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        marker.setTextColor(Color.WHITE);
        marker.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(160, 0, 119, 209));
        bg.setCornerRadius(dp(18));
        marker.setBackground(bg);
        WindowManager.LayoutParams p = overlayParams(dp(30), dp(30),
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.x = Math.max(0, clickX - dp(15));
        p.y = Math.max(0, clickY - dp(15));
        try { windowManager.addView(marker, p); }
        catch (Exception ignored) { marker = null; }
    }

    private void startLoop() {
        if (clickX < 0 || clickY < 0) {
            Toast.makeText(this, "먼저 ‘위치’를 눌러 클릭 지점을 정하세요", Toast.LENGTH_SHORT).show();
            return;
        }
        if (running) return;
        running = true;
        sequenceId++;
        updateStartButton();
        beginCycle();
    }

    private void beginCycle() {
        if (!running) return;
        // Each cycle begins with a 1.5-second long press.
        beginHold();
    }

    private void beginClickPhase() {
        if (!running) return;
        cycleStartUptime = SystemClock.uptimeMillis();
        scheduleClick(0);
    }

    private void scheduleClick(int index) {
        if (!running) return;
        long offset = index * CLICK_INTERVAL_MS;
        if (offset >= CLICK_PHASE_MS) {
            // Start the final wait after the nominal 3.5-second click phase.
            handler.postAtTime(this::beginFinalWait, Math.max(SystemClock.uptimeMillis(),
                    cycleStartUptime + CLICK_PHASE_MS));
            return;
        }
        long scheduledUptime = cycleStartUptime + offset;
        if (SystemClock.uptimeMillis() > scheduledUptime + CLICK_INTERVAL_MS) {
            scheduleClick(index + 1);
            return;
        }
        handler.postAtTime(() -> {
            if (!running) return;
            if (offset + CLICK_INTERVAL_MS >= CLICK_PHASE_MS) {
                // Wait until the final tap completes and the click phase reaches 3.5 seconds.
                final int currentSequenceId = sequenceId;
                dispatchStroke(TAP_DURATION_MS, new GestureResultCallback() {
                    private void scheduleFinalWait() {
                        if (running && currentSequenceId == sequenceId)
                            handler.postAtTime(AutoClickService.this::beginFinalWait,
                                    Math.max(SystemClock.uptimeMillis(),
                                            cycleStartUptime + CLICK_PHASE_MS));
                    }
                    @Override public void onCompleted(GestureDescription gesture) {
                        scheduleFinalWait();
                    }
                    @Override public void onCancelled(GestureDescription gesture) {
                        scheduleFinalWait();
                    }
                });
            } else {
                dispatchStroke(TAP_DURATION_MS, null);
                scheduleClick(index + 1);
            }
        }, Math.max(SystemClock.uptimeMillis(), scheduledUptime));
    }

    private void beginFinalWait() {
        if (!running) return;
        // Rest without gestures for 3 seconds before the next long press.
        handler.postDelayed(this::beginCycle, FINAL_WAIT_MS);
    }

    private void beginHold() {
        if (!running) return;
        final int currentSequenceId = sequenceId;
        dispatchStroke(HOLD_DURATION_MS, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                if (running && currentSequenceId == sequenceId) {
                    // Wait 6 seconds with no touches, then click every 100 ms for 3.5 seconds.
                    handler.postDelayed(AutoClickService.this::beginClickPhase, WAIT_DURATION_MS);
                }
            }
            @Override public void onCancelled(GestureDescription gesture) {
                if (running && currentSequenceId == sequenceId) {
                    handler.postDelayed(AutoClickService.this::beginCycle, 200);
                }
            }
        });
    }

    private void dispatchStroke(long duration, GestureResultCallback callback) {
        if (!running) return;
        Path path = new Path();
        path.moveTo(clickX, clickY);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        boolean accepted = dispatchGesture(gesture, callback, handler);
        if (!accepted && callback != null) {
            Toast.makeText(this, "제스처 실행 실패. 접근성 상태를 확인하세요", Toast.LENGTH_SHORT).show();
            stopLoop();
        }
    }

    private void stopLoop() {
        running = false;
        sequenceId++;
        handler.removeCallbacksAndMessages(null);
        updateStartButton();
    }

    private void updateStartButton() {
        if (startStop == null) return;
        startStop.setText(running ? "정지" : "시작");
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(running ? Color.rgb(173,73,70) : Color.rgb(33,130,96));
        bg.setCornerRadius(dp(10));
        startStop.setBackground(bg);
    }

    private WindowManager.LayoutParams overlayParams(int width, int height, int flags) {
        return new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                android.graphics.PixelFormat.TRANSLUCENT);
    }

    private void removeSelector() {
        if (selector != null && windowManager != null) {
            try { windowManager.removeView(selector); } catch (Exception ignored) { }
            selector = null;
        }
    }

    private void removeMarker() {
        if (marker != null && windowManager != null) {
            try { windowManager.removeView(marker); } catch (Exception ignored) { }
            marker = null;
        }
    }

    private void removeController() {
        if (controller != null && windowManager != null) {
            try { windowManager.removeView(controller); } catch (Exception ignored) { }
            controller = null;
            controllerParams = null;
            startStop = null;
        }
    }

    private void removeAllViews() {
        removeSelector();
        removeMarker();
        removeController();
    }

    private int dp(float d) { return Math.round(d * getResources().getDisplayMetrics().density); }
}
