package com.example.user.camera;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Date;
import java.sql.Time;
import java.text.SimpleDateFormat;

import static android.view.View.GONE;
import static com.example.user.camera.SettingActivity.PREF_CAR_IP;
import static com.example.user.camera.SettingActivity.PREF_CAR_CAMPORT;
import static com.example.user.camera.SettingActivity.PREF_CAR_CTLPORT;

public class MainActivity extends Activity implements View.OnClickListener,View.OnTouchListener {

    public static final String TAG = "MainActivity";
    private static final int PROGRESS_NONE = 1;
    private static final int PROGRESS_VISIBLE = 0;


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
    private ProgressBar progressBar;

    private boolean lightFlag = false;
    private boolean gravityFlag = false;
    private int olditems = -1;
    private int items = -1;// default: light off,gravity off

    private static MyThread mynewthread;
    private MjpegView mv = null;

    private int width = 640;
    private int height = 480;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initView();
        setListener();
        PreferenceManager.setDefaultValues(this, R.xml.pref_setting, false);
//        new DoRead().execute();
        mynewthread = new MyThread();
        mynewthread.start();     //ctl_client
        Log.d(TAG, "onCreate: ");
    }
    @Override
    protected void onResume() {
        Log.d(TAG, "onResume: ");
        super.onResume();
        if (!mv.isStreaming()) {
            new DoRead().execute();
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: ");
        super.onPause();
        if(mv.isStreaming()){
            mv.stopPlayback();  //mRun = false , suspending = true;
        }
    }
    private String getPreference(String key) {
        return PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString(key, "");
    }

    Handler myMessageHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case PROGRESS_NONE:
                    progressBar.setVisibility(View.GONE);
                    break;
                case PROGRESS_VISIBLE:
                    progressBar.setVisibility(View.VISIBLE);
                    break;
                default:
                    break;

            }
        }
    };
    private static Socket cam_socket = null;
    private static InputStream in = null;
    private static OutputStream out = null;


    public class DoRead extends AsyncTask<Integer, Integer, MjpegInputStream> {

        @Override
        protected MjpegInputStream doInBackground(Integer... params) {
            try{

                Message msgVisible = new Message();
                msgVisible.what = PROGRESS_VISIBLE;
                myMessageHandler.sendMessage(msgVisible);

                Log.d(TAG, "doInBackground: "+getPreference(PREF_CAR_IP)+getPreference(PREF_CAR_CAMPORT));
                String port = getPreference(PREF_CAR_CAMPORT);
                int portnum = Integer.parseInt(port);
//                socket = new Socket("192.168.1.233", 8080);
                cam_socket = new Socket(getPreference(PREF_CAR_IP),portnum);
                cam_socket.setTcpNoDelay(true); //实时性，客户端每发送一次数据，无论数据包大小都会将这些数据发送出去
                cam_socket.setReceiveBufferSize(65536);//64k
                cam_socket.setSendBufferSize(65536);
                cam_socket.setKeepAlive(true);//防止服务器端无效时，客户端长时间处于连接状态

                in = cam_socket.getInputStream();
                out = cam_socket.getOutputStream();

                //请求服务器的图片资源
                String s = new String("GET/stream.mjpeg\n");
                out.write(s.getBytes());
                out.flush();


                Message mNone = new Message();
                mNone.what = PROGRESS_NONE;
                myMessageHandler.sendMessage(mNone);
                
                return new MjpegInputStream(in);

            }catch (IOException e){

                e.printStackTrace();
            }
            return null;
        }
        protected void onPostExecute(MjpegInputStream result) {
            mv.setSource(result);
        }
    }

    public void initView() {

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

        progressBar = (ProgressBar) findViewById(R.id.progressbar);

        mv = (MjpegView) findViewById(R.id.mv);
        mv.setResolution(width,height);
        mv.setDisplayMode(MjpegView.SIZE_FULLSCREEN);
        mv.showFps(true);
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
    private void screenshot(String bitName)
    {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        Date curDate = new Date(System.currentTimeMillis());
        String filename = formatter.format(curDate);

        //得到SD卡的路径也设置文件名
        //这里可以简化的写成imageFilePath=Uri.parse("file:////sdcard/my.jpg");
        String imageFilePath=Environment.getExternalStorageDirectory()
                .getAbsolutePath()+"/"+filename+".jpg"; //sdcard 根目录

        Bitmap bmp = mv.getBitmap();
        if (bmp != null)
        {
            File file = new File(imageFilePath);
            if(file.exists()){
                file.delete();
            }
            FileOutputStream out;
            try{
                out = new FileOutputStream(file);
                if(bmp.compress(Bitmap.CompressFormat.PNG, 100, out))
                {
                    out.flush();
                    out.close();
                }
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
    @Override
    public void onClick(View v) {

        System.out.println("before :olditmes = "+olditems+",items = "+items);
        olditems = items;

        switch (v.getId()) {

            case R.id.camera:
                Toast.makeText(this, "capture", Toast.LENGTH_SHORT).show();
                screenshot("capture.jpg");
                break;
            case R.id.setting:

                startActivity(new Intent(this, SettingActivity.class));
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
                break;
        }
        System.out.println("after:olditmes = "+olditems+",items = "+items);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        System.out.println("before touch :olditmes = "+olditems+",items = "+items);

        olditems = items;
        switch (v.getId()){
            case R.id.go:
                if(event.getAction() == MotionEvent.ACTION_UP){
                    items = 4;  //stop
                    Toast.makeText(this, "stop", Toast.LENGTH_SHORT).show();
                }
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    items = 5;  //go
                    Toast.makeText(this, "go", Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.back:
                if(event.getAction() == MotionEvent.ACTION_UP){
                    items = 4;  //stop
                    Toast.makeText(this, "stop", Toast.LENGTH_SHORT).show();
                }
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    items = 6;  //back
                    Toast.makeText(this, "back", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.right:
                if(event.getAction() == MotionEvent.ACTION_UP){
                    items = 4;  //stop
                    Toast.makeText(this, "stop", Toast.LENGTH_SHORT).show();
                }
                if(event.getAction() == MotionEvent.ACTION_DOWN){

                    items = 7;  //right
                    Toast.makeText(this, "right", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.left:
                if(event.getAction() == MotionEvent.ACTION_UP){
                    items = 4;  //stop
                    Toast.makeText(this, "stop", Toast.LENGTH_SHORT).show();
                }
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    items = 8;  //left
                    Toast.makeText(this, "left", Toast.LENGTH_SHORT).show();
                }
                break;

            case R.id.camera_up:
                if(event.getAction() == MotionEvent.ACTION_UP){
                    items = 9;  //stop
                    Toast.makeText(this, "stop", Toast.LENGTH_SHORT).show();
                }
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    items = 10;  //camup
                    Toast.makeText(this, "camera_up", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.camera_down:
                if(event.getAction() == MotionEvent.ACTION_UP){
                    items = 9;  //stop
                    Toast.makeText(this, "stop", Toast.LENGTH_SHORT).show();
                }
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    items = 11;  //camdown
                    Toast.makeText(this, "camera_down", Toast.LENGTH_SHORT).show();
                }
                break;
        }
        System.out.println("after ontouch :olditmes = "+olditems+",items = "+items);
        return false;
    }

    private static Socket ctl_socket = null;
    private static OutputStream ctl_out = null;

    class MyThread extends Thread {
        @Override
        public void run() {
            super.run();

            while(true){
                try {

                    if (ctl_socket == null) {//判断socket是否为null，避免按钮多次按下时创建多个Socket对象
                        try {
                            String port = getPreference(PREF_CAR_CTLPORT);
                            int portnum = Integer.parseInt(port);
                            ctl_socket = new Socket(getPreference(PREF_CAR_IP), portnum);

                            ctl_socket.setTcpNoDelay(true); //实时性，客户端每发送一次数据，无论数据包大小都会将这些数据发送出去
                            ctl_socket.setKeepAlive(true);//防止服务器端无效时，客户端长时间处于连接状态


                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    else {
                        if(ctl_out == null)
                            //3、获取输入输出流对象
                            ctl_out = ctl_socket.getOutputStream();
                    }

                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(olditems != items) {
                    System.out.println("diff run :olditmes = "+olditems+",items = "+items);
                    String cmd = null;
                    switch(items){
                        case 0:
                            cmd = "lighton";
                            break;
                        case 1:
                            cmd = "lightoff";
                            break;
                        case 2:
                            cmd = "gravityoff";
                            break;
                        case 3:
                            cmd = "gravityon";
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
                            cmd = "camstop";
                            break;
                        case 10:
                            cmd = "camup";
                            break;
                        case 11:
                            cmd = "camdown";
                            break;
                        default:
                            break;
                    }
                    olditems = items;
                /* judge */
                    try {
                        ctl_out.write(cmd.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}

