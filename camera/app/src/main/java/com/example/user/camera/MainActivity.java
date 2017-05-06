package com.example.user.camera;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;


public class MainActivity extends Activity implements View.OnClickListener,View.OnTouchListener {

    private WebView webview1;

    private ImageButton imgBtn_light;
    private ImageButton imgBtn_gravity;
    private ImageButton imgBtn_setting;

    private ImageButton imgBtn_go;
    private ImageButton imgBtn_back;
    private ImageButton imgBtn_left;
    private ImageButton imgBtn_right;

    private ImageButton imgBtn_cameraup;
    private ImageButton imgBtn_cameradown;
    private ImageButton imgBtn_camera;

    private boolean lightFlag = false;
    private boolean gravityFlag = false;
    private int items = 0;// default: light off,gravity off

    private boolean settingFlag = false;
    private boolean cameraFlag = false;
    private boolean cameraUp = false;
    private boolean cameraDown = false;
    private boolean go = false;
    private boolean back = false;
    private boolean left = false;
    private boolean right = false;


    private MyThread mynewthread;


    private boolean sendConnectionCloseHeader = false;
    private MjpegView mv = null;

    private int width = 640;
    private int height = 480;
    private final String TAG = "MainActivity";
    private boolean suspending = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initView();
        setListener();

        new DoRead().execute();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume: ");
        super.onResume();

        if(mv != null){

            if(suspending){
                new DoRead().execute();
                suspending = false;
            }
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: ");
        super.onPause();

        if(mv != null){

            if(mv.isStreaming()){

                mv.stopPlayback();
                suspending = true;
            }
        }
    }

    private Socket socket = null;
    private InputStream in = null;
    private OutputStream out = null;

    public class DoRead extends AsyncTask<String, Void, MjpegInputStream> {

        @Override
        protected MjpegInputStream doInBackground(String... params) {
            try{

                socket = new Socket("192.168.1.233", 8080);
                in = socket.getInputStream();
                out = socket.getOutputStream();

                //请求服务器的图片资源
                String s = new String("GET/stream.mjpeg\n");
                out.write(s.getBytes());
                out.flush();

                return new MjpegInputStream(in);

            }catch (IOException e){

                e.printStackTrace();
            }
            return null;
        }
        protected void onPostExecute(MjpegInputStream result) {

            mv.setSource(result);
            mv.setDisplayMode(MjpegView.SIZE_FULLSCREEN);
            mv.showFps(true);
        }
    }

    public void initView() {

//        webview1 = (WebView) findViewById(R.id.webView1);

        imgBtn_light = (ImageButton) findViewById(R.id.light);
        imgBtn_gravity = (ImageButton) findViewById(R.id.gravity);
        imgBtn_setting = (ImageButton) findViewById(R.id.setting);

        imgBtn_go = (ImageButton) findViewById(R.id.go);
        imgBtn_back = (ImageButton) findViewById(R.id.back);
        imgBtn_right = (ImageButton) findViewById(R.id.right);
        imgBtn_left = (ImageButton) findViewById(R.id.left);

        imgBtn_camera = (ImageButton) findViewById(R.id.camera);
        imgBtn_cameradown = (ImageButton) findViewById(R.id.camera_down);
        imgBtn_cameraup = (ImageButton) findViewById(R.id.camera_up);

        mv = (MjpegView) findViewById(R.id.mv);

        if(mv != null){

            mv.setResolution(width,height);
        }
    }

    public void setListener() {

        imgBtn_light.setOnClickListener(this);
        imgBtn_gravity.setOnClickListener(this);
        imgBtn_setting.setOnClickListener(this);

        imgBtn_go.setOnTouchListener(this);
        imgBtn_back.setOnTouchListener(this);
        imgBtn_left.setOnTouchListener(this);
        imgBtn_right.setOnTouchListener(this);

        imgBtn_camera.setOnClickListener(this);
        imgBtn_cameraup.setOnTouchListener(this);
        imgBtn_cameradown.setOnTouchListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.camera:
                Toast.makeText(this, "capture", Toast.LENGTH_SHORT).show();
                cameraFlag = true;// take a photo
                mynewthread = new MyThread();
                mynewthread.start();
                break;
            case R.id.setting:

                cameraFlag = true;// take a photo
                mynewthread = new MyThread();
                mynewthread.start();
                Toast.makeText(this,"open setting page",Toast.LENGTH_SHORT).show();
                break;

            case R.id.light:
                if (lightFlag) {
                    Toast.makeText(this, "off light", Toast.LENGTH_SHORT).show();
                    lightFlag = false;
                    items = 0;  //turn off light

                } else {
                    Toast.makeText(this, "on light", Toast.LENGTH_SHORT).show();
                    lightFlag = true;
                    items = 1;//turn on light
                }
                mynewthread = new MyThread();
                mynewthread.start();
                break;

            case R.id.gravity:

                if (gravityFlag) {
                    Toast.makeText(this, "off gravity", Toast.LENGTH_SHORT).show();
                    gravityFlag = false;
                    items = 2;  //turn off gravity

                } else {
                    Toast.makeText(this, "on gravity", Toast.LENGTH_SHORT).show();
                    gravityFlag = true;
                    items = 3;  //turn on gravity
                }
                mynewthread = new MyThread();
                mynewthread.start();
                break;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        switch (v.getId()){
            case R.id.go:
                if(event.getAction() == MotionEvent.ACTION_UP){
                    items = 4;  //stop
                    Toast.makeText(this, "stop", Toast.LENGTH_SHORT).show();

                    mynewthread = new MyThread();
                    mynewthread.start();

                }
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    items = 5;  //go
                    Toast.makeText(this, "go", Toast.LENGTH_SHORT).show();

                    mynewthread = new MyThread();
                    mynewthread.start();

                }
                break;

            case R.id.back:
                if(event.getAction() == MotionEvent.ACTION_UP){
                    items = 4;  //stop
                    Toast.makeText(this, "stop", Toast.LENGTH_SHORT).show();
                    mynewthread = new MyThread();
                    mynewthread.start();
                }
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    items = 6;  //back
                    Toast.makeText(this, "back", Toast.LENGTH_SHORT).show();
                    mynewthread = new MyThread();
                    mynewthread.start();
                }
                break;
            case R.id.right:
                if(event.getAction() == MotionEvent.ACTION_UP){
                    items = 4;  //stop
                    Toast.makeText(this, "stop", Toast.LENGTH_SHORT).show();
                    mynewthread = new MyThread();
                    mynewthread.start();
                }
                if(event.getAction() == MotionEvent.ACTION_DOWN){

                    items = 7;  //right
                    Toast.makeText(this, "right", Toast.LENGTH_SHORT).show();
                    mynewthread = new MyThread();
                    mynewthread.start();
                }
                break;
            case R.id.left:
                if(event.getAction() == MotionEvent.ACTION_UP){
                    items = 4;  //stop
                    Toast.makeText(this, "stop", Toast.LENGTH_SHORT).show();

                    mynewthread = new MyThread();
                    mynewthread.start();
                }
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    items = 8;  //left
                    Toast.makeText(this, "left", Toast.LENGTH_SHORT).show();
                    mynewthread = new MyThread();
                    mynewthread.start();
                }
                break;
            case R.id.camera_up:
                if(event.getAction() == MotionEvent.ACTION_UP){
                    items = 9;  //stop
                    Toast.makeText(this, "stop", Toast.LENGTH_SHORT).show();
                    mynewthread = new MyThread();
                    mynewthread.start();
                }
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    items = 10;  //left
                    Toast.makeText(this, "camera_up", Toast.LENGTH_SHORT).show();
                    mynewthread = new MyThread();
                    mynewthread.start();
                }
                break;
            case R.id.camera_down:
                if(event.getAction() == MotionEvent.ACTION_UP){
                    items = 9;  //stop
                    Toast.makeText(this, "stop", Toast.LENGTH_SHORT).show();
                    mynewthread = new MyThread();
                    mynewthread.start();
                }
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    items = 11;  //left
                    Toast.makeText(this, "camera_down", Toast.LENGTH_SHORT).show();
                    mynewthread = new MyThread();
                    mynewthread.start();
                }
                break;
        }

        return false;
    }

    class MyThread extends Thread {
        @Override
        public void run() {
            super.run();

            try {

                if (socket == null) {//判断socket是否为null，避免按钮多次按下时创建多个Socket对象
                    try {
                        socket = new Socket("192.168.1.233", 8888);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                //3、获取输入输出流对象
                out = socket.getOutputStream();

            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            String cmd = null;
            switch(items){
                case 0:
                    cmd = "lighton";
                    break;
                case 1:
                    cmd = "lightoff";
                    break;
                case 2:
                    cmd = "Gravityoff";
                    break;
                case 3:
                    cmd = "Gravityon";
                    break;
                case 4:
                    cmd = "stop";
                    break;
                case 5:
                    cmd = "go";
                    break;
                case 6:
                    cmd = "back";
                    break;
                case 7:
                    cmd = "right";
                    break;
                case 8:
                    cmd = "left";
                    break;
                case 9:
                    cmd = "camera_stop";
                    break;
                case 10:
                    cmd = "camera_up";
                    break;
                case 11:
                    cmd = "camera_down";
                    break;
            }

            if (cameraFlag) {
                cmd = "taphoto";
                cameraFlag = false;
            }
                /* judge */
            try {
                out.write(cmd.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}

