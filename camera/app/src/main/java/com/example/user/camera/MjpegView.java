package com.example.user.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

public class MjpegView extends SurfaceView implements SurfaceHolder.Callback {

//    public static final String TAG = "MJPEG";
//
//    public final static int POSITION_UPPER_LEFT = 9;
//    public final static int POSITION_UPPER_RIGHT = 3;
//    public final static int POSITION_LOWER_LEFT = 12;
    public final static int POSITION_LOWER_RIGHT = 6;

    public final static int SIZE_STANDARD = 1;
    public final static int SIZE_BEST_FIT = 4;
    public final static int SIZE_FULLSCREEN = 8;

    SurfaceHolder holder;
    Context saved_context;

    private MjpegViewThread thread;
    private MjpegInputStream mIn = null;

    private boolean showFps = false;
    private boolean mRun = false;
    private boolean surfaceDone = false;

    //画文本
    private Paint overlayPaint;
    private int overlayTextColor;
    private int overlayBackgroundColor;
    private int ovlPos;

    //设置显示
    private int dispWidth;
    private int dispHeight;
    private int displayMode;

    private boolean suspending = false;
    private Bitmap bmp = null;

    // image size
    public int IMG_WIDTH = 640;
    public int IMG_HEIGHT = 480;


    public class MjpegViewThread extends Thread {

        private SurfaceHolder mSurfaceHolder;
        private int frameCounter = 0;   //计算帧率
        private long start;             //系统时间
        private String fps = "";


        public MjpegViewThread(SurfaceHolder surfaceHolder, Context context) {

            mSurfaceHolder = surfaceHolder;
        }

        private Rect destRect(int bmw, int bmh) {

            if (displayMode == MjpegView.SIZE_FULLSCREEN)
                return new Rect(0, 0, dispWidth, dispHeight);
            return null;
        }

        public void setSurfaceSize(int width, int height) {

            synchronized (mSurfaceHolder) {
                dispWidth = width;
                dispHeight = height;
            }
        }

        private Bitmap makeFpsOverlay(Paint p) {

            Rect b = new Rect();
            //得到文本的边界，上下左右，提取到bounds中，可以通过这计算文本的宽和高
            p.getTextBounds(fps, 0, fps.length(), b);


            // false indentation to fix forum layout             
            Bitmap bm = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888);

            Canvas c = new Canvas(bm);
            p.setColor(overlayBackgroundColor);
            c.drawRect(0, 0, b.width(), b.height(), p);
            p.setColor(overlayTextColor);
            c.drawText(fps, -b.left, b.bottom - b.top - p.descent(), p);
            return bm;
        }

        public void run() {

            start = System.currentTimeMillis();
            PorterDuffXfermode mode = new PorterDuffXfermode(PorterDuff.Mode.DST_OVER);

            int width;
            int height;
            Rect destRect = null;
            Canvas c = null;

            Paint p = new Paint();
            Bitmap ovl = null;

            while (mRun) {

                if (surfaceDone) try {

                    c = mSurfaceHolder.lockCanvas();
                    if(c == null){
                        continue;
                    }
                    synchronized (mSurfaceHolder) {
                        try {
                            bmp = mIn.readMjpegFrame();

                            destRect = destRect(bmp.getWidth(), bmp.getHeight());
                            c.drawColor(Color.BLACK);
                            c.drawBitmap(bmp, null, destRect, p);

                            //显示文本的帧率
                            if (showFps) {

                                p.setXfermode(mode);
                                if (ovl != null) {

                                    // false indentation to fix forum layout
                                    height = ((ovlPos & 1) == 1) ? destRect.top : destRect.bottom - ovl.getHeight();
                                    width = ((ovlPos & 8) == 8) ? destRect.left : destRect.right - ovl.getWidth();
                                    c.drawBitmap(ovl, width, height, null);
                                }
                                p.setXfermode(null);
                                frameCounter++;
                                if ((System.currentTimeMillis() - start) >= 1000) {

                                    fps = String.valueOf(frameCounter) + "fps";
                                    frameCounter = 0;
                                    start = System.currentTimeMillis();
                                    if (ovl != null) ovl.recycle();
                                    ovl = makeFpsOverlay(overlayPaint);
                                }
                            }
                        }catch (IOException e){

                        }
                    }
                } finally {
                    if (c != null) {
                        mSurfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }
    }

    private void init(Context context) {

        //SurfaceHolder holder = getHolder();
        holder = getHolder();
        saved_context = context;
        holder.addCallback(this);
        thread = new MjpegViewThread(holder, context);
        setFocusable(true);

        overlayPaint = new Paint();
        overlayPaint.setTextAlign(Paint.Align.LEFT);
        overlayPaint.setTextSize(12);
        overlayPaint.setTypeface(Typeface.DEFAULT);

        overlayTextColor = Color.WHITE;
        overlayBackgroundColor = Color.BLACK;

        ovlPos = MjpegView.POSITION_LOWER_RIGHT;    //6

        displayMode = MjpegView.SIZE_FULLSCREEN;
        dispWidth = getWidth();
        dispHeight = getHeight();

    }

    public void startPlayback() {

        if (mIn != null) {

            mRun = true;
            if (thread == null) {
                thread = new MjpegViewThread(holder, saved_context);
            }
            thread.start();
        }
    }

    public void resumePlayback() {

        if (suspending) {

            if (mIn != null) {

                mRun = true;
                SurfaceHolder holder = getHolder();
                holder.addCallback(this);
                thread = new MjpegViewThread(holder, saved_context);
                thread.start();
                suspending = false;
            }
        }
    }

    public void stopPlayback() {

        if (mRun) {

            suspending = true;
        }
        mRun = false;
        if (thread != null) {

            boolean retry = true;
            while (retry) {
                try {
                    thread.join();
                    retry = false;
                } catch (InterruptedException e) {
                }
            }
            thread = null;
        }
        if (mIn != null) {
            try {
                mIn.close();
            } catch (IOException e) {
            }
            mIn = null;
        }

    }

    public MjpegView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MjpegView(Context context) {
        super(context);
        init(context);
    }

    public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
        if (thread != null) {
            thread.setSurfaceSize(w, h);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceDone = false;
        stopPlayback();
    }

    public void surfaceCreated(SurfaceHolder holder) {
        surfaceDone = true;
    }

    public void showFps(boolean b) {
        showFps = b;
    }

    public void setSource(MjpegInputStream source) {

        mIn = source;
        if (!suspending) {
            startPlayback();
        } else {
            resumePlayback();
        }
    }

    public void setOverlayPaint(Paint p) {
        overlayPaint = p;
    }
    public void setOverlayTextColor(int c) {
        overlayTextColor = c;
    }
    public void setOverlayBackgroundColor(int c) {
        overlayBackgroundColor = c;
    }
    public void setOverlayPosition(int p) {
        ovlPos = p;
    }

    public void setDisplayMode(int s) {
        displayMode = s;
    }
    public void setResolution(int w, int h) {

        IMG_WIDTH = w;
        IMG_HEIGHT = h;
    }
    public boolean isStreaming() {
        return mRun;
    }
}
