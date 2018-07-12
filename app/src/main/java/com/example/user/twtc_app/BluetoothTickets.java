package com.example.user.twtc_app;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.spec.AlgorithmParameterSpec;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by USER on 2015/11/19.
 */
public class BluetoothTickets extends Activity {
    private String key="SET31275691$00000000000000000000";
    private MyDBHelper mydbHelper;
    TextView ResultTxt;
    String result="",DEVICE_ID,SPS_ID,getdata,qr,TICKET_NO,TK_CODE,TK_NAME;
    Button ReturnBtn;
    private ClipboardManager cbMgr;
    private ClipboardManager.OnPrimaryClipChangedListener mPrimaryClipChangedListener;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.bluetooth_tickets);
        //getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.titlebar);

        //判斷藍牙現在是否連接中
        if(BluetoothConnectSetting.connectedBluetoothDevices.size()==0){
            finish();
        }

        ReturnBtn=(Button)findViewById(R.id.ReturnBtn);
        ResultTxt=(TextView) findViewById(R.id.BResultTxt);

        //取得現在驗票的園區代碼與裝置代號
        Intent intent = getIntent();
        SPS_ID=intent.getStringExtra("SPS_ID");
        DEVICE_ID=intent.getStringExtra("DEVICE_ID");

        //SQLITE
        mydbHelper = new MyDBHelper(this);

        //設定藍牙收發資料
        try{
            setBT();
        }catch(Exception e){
            e.printStackTrace();
        }

        //掃描事件
        this.cbMgr=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        mPrimaryClipChangedListener=new ClipboardManager.OnPrimaryClipChangedListener() {
            public void onPrimaryClipChanged() {
                try{
                    //Toast.makeText(BluetoothTickets.this, "BluetoothTickets", Toast.LENGTH_SHORT).show();
                    setVibrate(100);
                    qr=cbMgr.getPrimaryClip().getItemAt(0).getText().toString();
                    String a=qr.substring(0,qr.length()-16);
                    String iv=qr.substring(qr.length() - 16);
                    byte[] descryptBytes=DcodeQRCODE(iv.getBytes("UTF-8"),key.getBytes("UTF-8"), Base64.decode(a, Base64.DEFAULT));
                    getdata = new String(descryptBytes);
                    TICKET_NO=getdata.split("@")[4];
                    sendData("Q?"+TICKET_NO+"?"+DEVICE_ID+"?"+SPS_ID);
                }catch(Exception ex){
                    WriteLog.appendLog("BluetoothTickets.java/非花博票券條碼"+ex.toString());
                    setResultText(result = "票券狀態：驗票失敗，非花博票券條碼");
                    ResultTxt.setTextColor(Color.RED);
                }
            }
        };
        cbMgr.addPrimaryClipChangedListener( mPrimaryClipChangedListener);

        //回上頁按鈕
        ReturnBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        //監聽藍芽狀態
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mReceiver, filter);
    }//END ONCREATE

    @Override
    protected void onPause() {
        super.onPause();
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    //設定藍牙收發資料
    void setBT() throws IOException
    {
        mmOutputStream = BluetoothConnectSetting.mmSocket.getOutputStream();
        mmInputStream = BluetoothConnectSetting.mmSocket.getInputStream();
        beginListenForData();
    }

    //透過藍牙發送資料
    void sendData(String data)
    {
        try
        {
            mmOutputStream.write(data.getBytes());
        }
        catch (IOException ex)
        {
            //Toast.makeText(BluetoothTickets.this, "驗票錯誤，"+ex.toString(), Toast.LENGTH_SHORT).show();
            WriteLog.appendLog("BluetoothTickets.java/"+ex.toString());
        }
    }

    //接收透過藍牙所傳過來的資料
    void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "UTF-8");
                                    readBufferPosition = 0;
                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            if (data.split("\\?")[0].indexOf("OPEN") > -1) {
                                                TK_CODE=getdata.split("@")[5];
                                                TK_NAME=data.split("\\?")[1];
                                                setResultText(result = "票券狀態：驗票成功" + "\n\n票券號碼：" +TICKET_NO+ "\n\n票券種類：" +TK_NAME+ "\n\n票券入場紀錄："+getDateTime());
                                                ResultTxt.setTextColor(Color.BLUE);
                                                String[] ResultArray=new String[10];
                                                SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
                                                Calendar c = Calendar.getInstance();
                                                ResultArray[0]="A";
                                                ResultArray[1]=TICKET_NO;
                                                ResultArray[2]=SPS_ID;
                                                ResultArray[3]="I";
                                                ResultArray[4]=DEVICE_ID;
                                                ResultArray[5]=TK_CODE;
                                                ResultArray[6]=qr;
                                                ResultArray[7]=df.format(c.getTime());
                                                ResultArray[8]="";
                                                ResultArray[9]=getDateTime();
                                                mydbHelper.InsertToSQLiteUltraLight03(ResultArray, "OK");
                                            }else if(data.split("\\?")[0].indexOf("逾時") > -1){
                                                setResultText(result = "票券狀態：驗票失敗，" +data.split("\\?")[0]+ "\n\n票券號碼："+getdata.split("@")[4] + "\n\n票券種類："+data.split("\\?")[1] + "\n\n票券入場紀錄：");
                                                ResultTxt.setTextColor(Color.RED);
                                            }else if(data.split("\\?")[0].indexOf("已入場") > -1){
                                                setResultText(result = "票券狀態：驗票失敗，" +data.split("\\?")[0]+ "\n\n票券號碼："+getdata.split("@")[4] + "\n\n票券種類："+data.split("\\?")[1] + "\n\n票券入場紀錄："+data.split("\\?")[2]);
                                                ResultTxt.setTextColor(Color.RED);
                                            }else if(data.split("\\?")[0].indexOf("作廢") > -1){
                                                setResultText(result = "票券狀態：驗票失敗，" +data.split("\\?")[0]+ "\n\n票券號碼："+getdata.split("@")[4] + "\n\n票券種類："+data.split("\\?")[1] + "\n\n票券入場紀錄：");
                                                ResultTxt.setTextColor(Color.RED);
                                            }else if(data.split("\\?")[0].indexOf("無此售票") > -1){
                                                setResultText(result = "票券狀態：驗票失敗，" +data.split("\\?")[0]+ "\n\n票券號碼：" + "\n\n票券種類：" + "\n\n票券入場紀錄：");
                                                ResultTxt.setTextColor(Color.RED);
                                            }else if(data.split("\\?")[0].indexOf("非法票券") > -1){
                                                setResultText(result = "票券狀態：驗票失敗，非花博票券條碼" + "\n\n票券號碼：" + "\n\n票券種類：" + "\n\n票券入場紀錄：");
                                                ResultTxt.setTextColor(Color.RED);
                                            }
                                            //Toast.makeText(BluetoothTickets.this, data, Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });
        workerThread.start();
    }

    //QRCODE解碼
    public static byte[] DcodeQRCODE (byte[] ivBytes, byte[] keyBytes,byte[] textBytes) {
        try {
            AlgorithmParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            SecretKeySpec newKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, newKey, ivSpec);
            return cipher.doFinal(textBytes);
        } catch (Exception ex) {
            return null;
        }
    }

    //驗票結果訊息
    private void setResultText(String text) {
        ResultTxt.setText(text);
    }

    //取得現在時間 yyyy-MM-dd HH:mm:ss.SSS
    public String getDateTime(){
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Calendar c = Calendar.getInstance();
        String str = df.format(c.getTime());
        return str;
    }

    //震動
    public void setVibrate(int time){
        Vibrator myVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        myVibrator.vibrate(time);
    }

    //監聽藍芽連接狀態的廣播
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothConnectSetting.connectedBluetoothDevices.remove(device);
                Toast.makeText(BluetoothTickets.this, "藍牙連線關閉", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    };
}
