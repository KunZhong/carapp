package com.example.user.camera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.sql.Date;
import java.text.SimpleDateFormat;

import static com.example.user.camera.SettingActivity.PREF_CAR_CAMPORT;
import static com.example.user.camera.SettingActivity.PREF_CAR_CTLPORT;
import static com.example.user.camera.SettingActivity.PREF_CAR_IP;

public class MainActivity extends Activity implements SensorEventListener, View.OnClickListener, View.OnTouchListener {

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

    private boolean camupFlag = false;
    private boolean camdownFlag = false;

    private boolean lightFlag = false;
    private boolean gravityFlag = false;
    private int olditems = -1;
    private int items = -1;// default: light off,gravity off

    private static MyThread mynewthread;
    private MjpegView mv = null;

    private int width = 640;
    private int height = 480;
    private boolean isRun = false;

    private SensorManager mSensorManager;
    private Sensor sensor;
    private float mLastX, mLastY;

    private static final int G_MODE = 0;  //gravity_mode
    private static final int B_MODE = 1;  //btn_mode
    private static int MODE = B_MODE;   //init Btn_mode
    private int oldgflag = -1;
    private int gflag =-1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initView();
        setListener();
        PreferenceManager.setDefaultValues(this, R.xml.pref_setting, false);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (mSensorManager == null) {
            Log.v("sensor..", "Sensors not supported");
        }
        //选取加速度感应器
        sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);

    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume: ");
        super.onResume();

        if (!mv.isStreaming()) {
            new DoRead().execute();
        }
        if (!isRun) {
            mynewthread = new MyThread();
            mynewthread.start();     //ctl_client
            isRun = true;
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: ");
        super.onPause();

        if (mv.isStreaming()) {
            mv.stopPlayback();  //mRun = false , suspending = true;
        }
        if (isRun) {

            if (mynewthread != null) {
                try {
                    mynewthread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mynewthread = null;
            }
            isRun = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
            mSensorManager = null;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "onAccuracyChanged");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor == null) {
            return;
        }
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mLastX = event.values[0];//x
            mLastY = event.values[1];//y

            int x = (int) mLastX;
            int y = (int) mLastY;

//            Log.d(TAG, "onSensorChanged: x:"+x);
//            Log.d(TAG, "onSensorChanged: y:"+y);

            oldgflag = gflag;

            if (x <= -3 && y >= -8 && y <= 8) {
                //go
                gflag = 1;
            } else if (x >= 3 && y >= -8 && y <= 8) {
                //back
                gflag = 2;
            } else if (y <= -3 && x >= -8 && x <= 8) {
                //left
                gflag = 3;
            } else if (y >= 3 && x >= -8 && x <= 8) {
                //right
                gflag = 4;
            } else {
                gflag = 0;
                //stop
            }
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
            try {

                Message msgVisible = new Message();
                msgVisible.what = PROGRESS_VISIBLE;
                myMessageHandler.sendMessage(msgVisible);

                Log.d(TAG, "doInBackground: " + getPreference(PREF_CAR_IP) + getPreference(PREF_CAR_CAMPORT));
                String port = getPreference(PREF_CAR_CAMPORT);
                int portnum = Integer.parseInt(port);
//                socket = new Socket("192.168.1.233", 8080);
//                cam_socket = new Socket(getPreference(PREF_CAR_IP),portnum);

                cam_socket = new Socket();
                SocketAddress socAddress = new InetSocketAddress(getPreference(PREF_CAR_IP), portnum);
                cam_socket.connect(socAddress, 3000);//timeout 3s


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

            } catch (IOException e) {

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
        mv.setResolution(width, height);
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
        imgBtn_cameraup.setOnClickListener(this);
        imgBtn_cameradown.setOnClickListener(this);
    }

    private void screenshot(String bitName) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        Date curDate = new Date(System.currentTimeMillis());
        String filename = formatter.format(curDate);

        //得到SD卡的路径也设置文件名
        //这里可以简化的写成imageFilePath=Uri.parse("file:////sdcard/my.jpg");
        String imageFilePath = Environment.getExternalStorageDirectory()
                .getAbsolutePath() + "/" + filename + ".jpg"; //sdcard 根目录

        Bitmap bmp = mv.getBitmap();
        if (bmp != null) {
            File file = new File(imageFilePath);
            if (file.exists()) {
                file.delete();
            }
            FileOutputStream out;
            try {
                out = new FileOutputStream(file);
                if (bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    out.flush();
                    out.close();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onClick(View v) {

        olditems = items;

        switch (v.getId()) {

            case R.id.camera_up:
                if (camupFlag) {
                    camupFlag = false;
                    items = 9;
                } else {
                    camupFlag = true;
                    items = 10;
                }
                break;
            case R.id.camera_down:
                if (camdownFlag) {
                    camdownFlag = false;
                    items = 11;
                } else {
                    camdownFlag = true;
                    items = 12;
                }
                break;
            case R.id.camera:
                Toast.makeText(this, "capture", Toast.LENGTH_SHORT).show();
                screenshot("capture.jpg");
                break;
            case R.id.setting:

                startActivity(new Intent(this, SettingActivity.class));
                break;

            case R.id.light:
                if (lightFlag) {
                    lightFlag = false;
                    imgBtn_light.setImageResource(R.drawable.btn_light_off);
                    items = 0;  //turn off light

                } else {
                    lightFlag = true;
                    imgBtn_light.setImageResource(R.drawable.btn_light_on);
                    items = 1;//turn on light
                }
                break;

            case R.id.gravity:

                if (gravityFlag) {
                    gravityFlag = false;
                    imgBtn_gravity.setImageResource(R.drawable.btn_gravity_off);
                    MODE = B_MODE;
                    items = 2;  //turn off gravity

                } else {
                    gravityFlag = true;
                    imgBtn_gravity.setImageResource(R.drawable.btn_gravity_on);
                    MODE = G_MODE;
                    items = 3;  //turn on gravity
                }
                break;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        olditems = items;
        switch (v.getId()) {
            case R.id.go:
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    items = 4;  //stop
                    imgBtn_go.setBackground(getResources().getDrawable(R.drawable.btn_camera_up));
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    items = 5;  //go
                    imgBtn_go.setBackground(getResources().getDrawable(R.drawable.btn_camera_up2));
                }
                break;

            case R.id.back:
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    items = 4;  //stop
                    imgBtn_back.setBackground(getResources().getDrawable(R.drawable.btn_camera_down));

                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    items = 6;  //back
                    imgBtn_back.setBackground(getResources().getDrawable(R.drawable.btn_camera_down2));

                }
                break;
            case R.id.right:
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    items = 4;  //stop
                    imgBtn_right.setBackground(getResources().getDrawable(R.drawable.btn_go_right));

                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {

                    items = 7;  //right
                    imgBtn_right.setBackground(getResources().getDrawable(R.drawable.btn_go_right2));

                }
                break;
            case R.id.left:
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    items = 4;  //stop
                    imgBtn_left.setBackground(getResources().getDrawable(R.drawable.btn_go_left));

                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    items = 8;  //left
                    imgBtn_left.setBackground(getResources().getDrawable(R.drawable.btn_go_left2));

                }
                break;

        }
        return false;
    }

    private static Socket ctl_socket = null;
    private static OutputStream ctl_out = null;

    class MyThread extends Thread {
        @Override
        public void run() {
            super.run();

            if (ctl_socket == null) {//判断socket是否为null，避免按钮多次按下时创建多个Socket对象
                try {
                    String port = getPreference(PREF_CAR_CTLPORT);
                    int portnum = Integer.parseInt(port);
//                            ctl_socket = new Socket(getPreference(PREF_CAR_IP), portnum);
                    ctl_socket = new Socket();
                    SocketAddress socAddress = new InetSocketAddress(getPreference(PREF_CAR_IP), portnum);
                    ctl_socket.connect(socAddress, 3000);//timeout 3s


                    ctl_socket.setTcpNoDelay(true); //实时性，客户端每发送一次数据，无论数据包大小都会将这些数据发送出去
                    ctl_socket.setKeepAlive(true);//防止服务器端无效时，客户端长时间处于连接状态

                    ctl_out = ctl_socket.getOutputStream();

                    sendCmd();

                } catch (IOException e) {

                    ctl_socket = null;
                    ctl_out = null;
                    e.printStackTrace();
                }
            }
        }
    }

    public void sendCmd() {

        while (isRun) {

            if (olditems != items) {
                String cmd = null;
                switch (items) {
                    case 0:
                        cmd = "CTL/lightoff\n";
                        break;
                    case 1:
                        cmd = "CTL/lighton\n";
                        break;
                    case 2:
                        cmd = "CTL/gravityoff\n";
                        break;
                    case 3:
                        cmd = "CTL/gravityon\n";
                        break;
                    case 9:
                    case 10:
                        cmd = "CTL/camup\n";
                        break;
                    case 11:
                    case 12:
                        cmd = "CTL/camdown\n";
                        break;
                    default:
                        cmd = null;
                        break;
                }
                if(cmd != null){

                    olditems = items;
                    if (ctl_out != null) {

                        try {
                            ctl_out.write(cmd.getBytes());
                            ctl_out.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                Log.d(TAG, "sendCmd: cmd:"+cmd);
            }
            if (MODE == B_MODE) {

                if (olditems != items) {

                    String bmode_cmd = null;
                    switch (items) {
                        case 4:
                            bmode_cmd = "CTL/stop\n";
                            break;
                        case 5:
                            bmode_cmd = "CTL/go\n";
                            break;
                        case 6:
                            bmode_cmd = "CTL/back\n";
                            break;
                        case 7:
                            bmode_cmd = "CTL/right\n";
                            break;
                        case 8:
                            bmode_cmd = "CTL/left\n";
                            break;
                        default:
                            bmode_cmd = null;
                            break;
                    }
                    if(bmode_cmd != null){

                        olditems = items;
                        if (ctl_out != null) {

                            try {
                                ctl_out.write(bmode_cmd.getBytes());
                                ctl_out.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    Log.d(TAG, "sendCmd: bmode_cmd:"+bmode_cmd);
                }
            } else if (MODE == G_MODE) {

                if(oldgflag != gflag){

                    String gmode_cmd = null;
                    switch (gflag) {
                        case 0:
                            gmode_cmd = "CTL/stop\n";
                            break;
                        case 1:
                            gmode_cmd = "CTL/go\n";
                            break;
                        case 2:
                            gmode_cmd = "CTL/back\n";
                            break;
                        case 3:
                            gmode_cmd = "CTL/left\n";
                            break;
                        case 4:
                            gmode_cmd = "CTL/right\n";
                            break;
                        default:
                            gmode_cmd = null;
                            break;
                    }
                    if(gmode_cmd != null){

                        oldgflag = gflag;
                        if (ctl_out != null) {

                            try {
                                ctl_out.write(gmode_cmd.getBytes());
                                ctl_out.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    Log.d(TAG, "sendCmd: gmode_cmd:"+gmode_cmd);
                }
            }
        }
    }
}

