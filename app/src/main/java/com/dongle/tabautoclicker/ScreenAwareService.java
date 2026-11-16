package com.dongle.tabautoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

/**
 * Experimental screenshot-aware controller. Requires Android 11+.
 * Does not use the numeric tap counter. Completion requires both gauge ends to be lime green.
 */
public class ScreenAwareService extends AccessibilityService {
    private enum State { WAIT_CAST, HOLDING, WAIT_TAP, TAPPING, WAIT_RESET }
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextRecognizer recognizer;
    private WindowManager wm;
    private LinearLayout controls;
    private TextView status;
    private boolean running, screenshotBusy, gestureBusy;
    private int generation, x = -1, y = -1;
    private State state = State.WAIT_CAST;
    private long phaseStart;
    private static final long MAX_TAP_MS = 20000;
    private static final long MAX_WAIT_MS = 60000;
    private static final int SAMPLE_MS = 100;
    private long lastTapOcr = 0;
    private SharedPreferences prefs;
    private static ScreenAwareService instance;
    public static ScreenAwareService getInstance() { return instance; }

    @Override public void onServiceConnected() {
        instance = this;
        wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        prefs = getSharedPreferences("autoclick_settings", MODE_PRIVATE);
        x = prefs.getInt("clickX", -1);
        y = prefs.getInt("clickY", -1);
        recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        showController();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { stop(); }
    @Override public void onDestroy() {
        stop();
        if (controls != null) { try { wm.removeView(controls); } catch (Exception ignored) {} }
        if (recognizer != null) recognizer.close();
        instance = null;
        super.onDestroy();
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(12); b.setAllCaps(false);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(8,0,8,0);
        return b;
    }
    public void showController() {
        if (wm == null || controls != null) return;
        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(5,3,5,3);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(25,30,45)); bg.setCornerRadius(16);
        controls.setBackground(bg);
        TextView handle = new TextView(this);
        handle.setText("☷"); handle.setTextColor(Color.WHITE); handle.setTextSize(23);
        handle.setPadding(10,5,10,5);
        controls.addView(handle);
        Button position = button("위치");
        controls.addView(position);
        position.setOnClickListener(v -> choosePoint());
        Button toggle = button("시작");
        controls.addView(toggle);
        toggle.setOnClickListener(v -> { if(running) stop(); else start(); });
        status = new TextView(this);
        status.setTextColor(Color.WHITE); status.setTextSize(11);
        status.setText("대기");
        status.setPadding(5,0,5,0);
        controls.addView(status);
        WindowManager.LayoutParams p = overlayParams(-2,-2,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.x=12; p.y=130;
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX,downY; int initialX,initialY;
            public boolean onTouch(View v, MotionEvent e) {
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
                    downX=e.getRawX();downY=e.getRawY();initialX=p.x;initialY=p.y;return true;
                }
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE) {
                    p.x=initialX+(int)(e.getRawX()-downX);
                    p.y=initialY+(int)(e.getRawY()-downY);
                    try {wm.updateViewLayout(controls,p);}catch(Exception ignored){}
                }
                return true;
            }
        });
        toggle.setTag("toggle");
        try {wm.addView(controls,p);}catch(Exception e){controls=null;}
    }
    private WindowManager.LayoutParams overlayParams(int w,int h,int flags) {
        return new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,android.graphics.PixelFormat.TRANSLUCENT);
    }
    private void choosePoint() {
        stop();
        View cover = new View(this);
        cover.setBackgroundColor(0x55000000);
        WindowManager.LayoutParams p = overlayParams(-1,-1,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        p.gravity=Gravity.TOP | Gravity.LEFT;
        cover.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_UP){
                x=(int)e.getRawX();y=(int)e.getRawY();
                prefs.edit().putInt("clickX",x).putInt("clickY",y).apply();
                try {wm.removeView(cover);}catch(Exception ignored){}
                Toast.makeText(this,"클릭 위치 저장: "+x+", "+y,Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        wm.addView(cover,p);
    }
    private void setState(State next,String label) {
        state=next; phaseStart=SystemClock.uptimeMillis();
        if(status!=null)status.setText(label);
    }
    private void start() {
        if(Build.VERSION.SDK_INT<30) {
            Toast.makeText(this,"화면 인식 테스트는 Android 11 이상이 필요합니다",Toast.LENGTH_LONG).show();
            return;
        }
        if(x<0 || y<0) {Toast.makeText(this,"먼저 위치를 지정하세요",Toast.LENGTH_SHORT).show();return;}
        running=true;generation++;gestureBusy=false;screenshotBusy=false;
        setState(State.WAIT_CAST,"캐스트 감지");
        for(int i=0;i<controls.getChildCount();i++) if("toggle".equals(controls.getChildAt(i).getTag()))
            ((Button)controls.getChildAt(i)).setText("정지");
        main.post(this::poll);
    }
    private void stop() {
        running=false;generation++;main.removeCallbacksAndMessages(null);
        screenshotBusy=false;gestureBusy=false;
        if(status!=null)status.setText("정지");
        if(controls!=null)for(int i=0;i<controls.getChildCount();i++)
            if("toggle".equals(controls.getChildAt(i).getTag()))((Button)controls.getChildAt(i)).setText("시작");
    }
    private void poll() {
        if(!running)return;
        long elapsed=SystemClock.uptimeMillis()-phaseStart;
        if(state==State.TAPPING && elapsed>MAX_TAP_MS) {
            setState(State.WAIT_RESET,"클릭 제한시간");
        } else if((state==State.WAIT_TAP || state==State.WAIT_RESET) && elapsed>MAX_WAIT_MS) {
            setState(State.WAIT_CAST,"대기 재시작");
        }
        if(!screenshotBusy && state!=State.HOLDING) capture(generation);
        main.postDelayed(this::poll,SAMPLE_MS);
    }
    private void capture(int token) {
        if(Build.VERSION.SDK_INT<30)return;
        screenshotBusy=true;
        try {
            takeScreenshot(0,getMainExecutor(),new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult result) {
                    if(!running || token!=generation){screenshotBusy=false;return;}
                    Bitmap hardware=null,bitmap=null;
                    try {
                        hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());
                        if(hardware!=null)bitmap=hardware.copy(Bitmap.Config.ARGB_8888,false);
                    }catch(Exception ignored){}
                    finally {result.getHardwareBuffer().close(); if(hardware!=null)hardware.recycle();}
                    if(bitmap==null){screenshotBusy=false;return;}
                    process(bitmap,token);
                }
                @Override public void onFailure(int errorCode) {screenshotBusy=false;}
            });
        }catch(Exception e){screenshotBusy=false;}
    }
    private void process(Bitmap frame,int token) {
        if(!running || token!=generation){frame.recycle();screenshotBusy=false;return;}
        if(state==State.TAPPING) {
            if(gaugeFull(frame)) {
                frame.recycle();screenshotBusy=false;
                setState(State.WAIT_CAST,"다음 캐스트 감지");
                return;
            }
            long now=SystemClock.uptimeMillis();
            if(now-lastTapOcr<450 || now-phaseStart<700) {
                frame.recycle();screenshotBusy=false;return;
            }
            lastTapOcr=now;
        }
        // Cast prompt is beside the character around screen center; tap prompt is at the bottom.
        int top=(int)(frame.getHeight()*
            (state==State.WAIT_TAP ? .68f : .25f));
        int bottom=(int)(frame.getHeight()*
            (state==State.WAIT_TAP ? 1.0f : .75f));
        Bitmap cropped=Bitmap.createBitmap(frame,0,top,frame.getWidth(),bottom-top);
        InputImage image=InputImage.fromBitmap(cropped,0);
        recognizer.process(image).addOnSuccessListener(result->{
            if(!running || token!=generation)return;
            String s=result.getText().replaceAll("\\s+","").toLowerCase();
            boolean cast=s.contains("캐스트하기") || s.contains("길게눌러");
            if(cast && (state==State.WAIT_CAST || state==State.WAIT_RESET || state==State.TAPPING)) {
                setState(State.HOLDING,"캐스팅");
                gesture(1500,()->setState(State.WAIT_TAP,"탭 대기"));
            } else if(state==State.WAIT_TAP && (s.contains("빠르게탭") || s.contains("탭하세요"))) {
                setState(State.TAPPING,"연속 클릭");
                tap();
            }
        }).addOnCompleteListener(task->{cropped.recycle();frame.recycle();screenshotBusy=false;});
    }
    private boolean lime(int c) {
        int r=Color.red(c),g=Color.green(c),b=Color.blue(c);
        return g>=145 && g>r*1.12 && g>b*1.25 && r>=45 && b<175;
    }
    private boolean gaugeFull(Bitmap bmp) {
        // Coordinates derived from the supplied 1536x627 landscape capture.
        // Sample narrow strips at BOTH inner ends of the dark gauge track.
        int w=bmp.getWidth(),h=bmp.getHeight();
        if(w<h)return false;
        return stripLime(bmp,.282f,.935f) && stripLime(bmp,.685f,.935f);
    }
    private boolean stripLime(Bitmap bmp,float xf,float yf) {
        int cx=(int)(bmp.getWidth()*xf),cy=(int)(bmp.getHeight()*yf);
        int hits=0,total=0;
        for(int dy=-5;dy<=5;dy+=2)for(int dx=-5;dx<=5;dx+=2) {
            int px=cx+dx,py=cy+dy;
            if(px>=0&&py>=0&&px<bmp.getWidth()&&py<bmp.getHeight()){
                total++;if(lime(bmp.getPixel(px,py)))hits++;
            }
        }
        return total>0 && hits>=total*.45f;
    }
    private void tap() {
        if(!running || state!=State.TAPPING || gestureBusy)return;
        gesture(35,()->main.postDelayed(()->{
            if(running && state==State.TAPPING)tap();
        },35));
    }
    private void gesture(long duration,Runnable done) {
        if(!running)return;
        gestureBusy=true;
        Path path=new Path();path.moveTo(x,y);
        GestureDescription gd=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
        int token=generation;
        boolean accepted=dispatchGesture(gd,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gesture) {finish();}
            @Override public void onCancelled(GestureDescription gesture) {finish();}
            private void finish(){
                if(token!=generation)return;
                gestureBusy=false;
                if(running && done!=null)done.run();
            }
        },main);
        if(!accepted){gestureBusy=false;stop();}
    }
}
