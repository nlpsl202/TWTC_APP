package com.example.user.twtc_app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.sql.Connection;

/**
 * Created by USER on 2015/11/17.
 */
public class AfterLogin  extends Activity {

    Button BluetoothTicketBtn,WifiTicketBtn,OffineTicketBtn,OfflineExportBtn,ConnectSettingBtn;
    private MyDBHelper mydbHelper;
    //SQL SERVER //建立連線
    ConnectionClass connectionClass;
    Connection con;
    Dialog alertDialog;
    File file;
    EditText pass_et;
    XmlHelper xmlHelper;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.after_login);

        xmlHelper=new XmlHelper(getFilesDir()+"//connectData.xml");
        //requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        //getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.titlebar);

        //UserDeviceTxt=(TextView)findViewById(R.id.UserDeviceTxt);
        //InternetStatusTxt=(TextView)findViewById(R.id.InternetStatusTxt);
        //BluetoothStatusTxt=(TextView)findViewById(R.id.BluetoothStatusTxt);
        //DeviceIdTxt=(TextView)findViewById(R.id.DeviceIdTxt);

        //取得從登入頁面傳送來的使用者帳號
        //Intent intent = getIntent();
        //SPS_ID = intent.getStringExtra("SPS_ID");

        //SQLITE
        //mydbHelper = new MyDBHelper(this);

        //取得手機資訊服務
        //TelephonyManager mTelManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        //取得手機IMEI碼
        //if ( ContextCompat.checkSelfPermission( this, android.Manifest.permission.READ_PHONE_STATE ) != PackageManager.PERMISSION_GRANTED ) {
        //    return;
        //}
        //String IMEI= mTelManager.getDeviceId();
        //DEVICE_ID=mydbHelper.selectIMEIdeviceID(IMEI);
        //if(DEVICE_ID.equals("")){
        //    DEVICE_ID="無法偵測";
        //}

        //DeviceIdTxt.setText("裝置代號："+DEVICE_ID);
        //UserDeviceTxt.setText("園區代碼：  "+SPS_ID+"\n"+"裝置代號：  "+DEVICE_ID);

        //執行SQL SERVER驗票SP
        //connectionClass = new ConnectionClass();
        //con= connectionClass.CONN();
        //如果登入後有網路的話，則呼叫登入的SP進行資料庫登入紀錄
        /*if(checkInternetConnect()){
            mydbHelper.executeLoginStoredProcedure(con,DEVICE_ID,SPS_ID);
        }*/

        BluetoothTicketBtn=(Button)findViewById(R.id.BluetoothTicketBtn);
        WifiTicketBtn=(Button)findViewById(R.id.WifiTicketBtn);
        OffineTicketBtn=(Button)findViewById(R.id.OffineTicketBtn);
        OfflineExportBtn=(Button)findViewById(R.id.OfflineExportBtn);
        ConnectSettingBtn=(Button)findViewById(R.id.ConnectSettingBtn);

        file = new File(getFilesDir()+"//connectData.xml");

        //切換至藍牙驗票
        BluetoothTicketBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent callSub = new Intent();
                callSub.setClass(AfterLogin.this, BluetoothConnectSetting.class);
                startActivityForResult(callSub, 0);
            }

        });

        //切換至WiFi驗票
        WifiTicketBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    Intent callSub = new Intent();
                    callSub.setClass(AfterLogin.this, OnlineTickets.class);
                    startActivityForResult(callSub, 0);
            }
        });

        //切換至離線驗票
        OffineTicketBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent callSub = new Intent();
                callSub.setClass(AfterLogin.this, OfflineTickets.class);
                startActivityForResult(callSub, 0);
            }
        });

        //切換至離線資料上傳
        OfflineExportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent callSub = new Intent();
                callSub.setClass(AfterLogin.this, OfflineExport.class);
                startActivityForResult(callSub, 0);
            }
        });

        //切換至系統設定
        ConnectSettingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    alertDialog = new Dialog(AfterLogin.this);
                    alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    alertDialog.setContentView(R.layout.activity_main_alert);
                    alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    pass_et =(EditText)alertDialog.findViewById(R.id.pass_et);
                    Button a =(Button)alertDialog.findViewById(R.id.ConfirmBtn);
                    a.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if(file.exists()){
                                if(!pass_et.getText().toString().equals(xmlHelper.ReadValue("SetupPassWord"))&&xmlHelper.ReadValue("SetupPassWord")!=""){
                                    Toast.makeText(AfterLogin.this, "密碼錯誤", Toast.LENGTH_SHORT).show();
                                }else{
                                    alertDialog.cancel();
                                    Intent callSub = new Intent();
                                    callSub.setClass(AfterLogin.this, ConnectSetting.class);
                                    startActivityForResult(callSub, 0);
                                }
                            }else{
                                if(!pass_et.getText().toString().equals("0000")){
                                    Toast.makeText(AfterLogin.this, "密碼錯誤", Toast.LENGTH_SHORT).show();
                                }else{
                                    alertDialog.cancel();
                                    Intent callSub = new Intent();
                                    callSub.setClass(AfterLogin.this, ConnectSetting.class);
                                    startActivityForResult(callSub, 0);
                                }
                            }
                        }
                    });
                    Button b =(Button)alertDialog.findViewById(R.id.CancelBtn);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            alertDialog.cancel();
                        }
                    });
                    alertDialog.show();
                }catch (Exception ex){

                }
            }
        });

        //IntentFilter intentFilter = new IntentFilter();
        //intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        //registerReceiver(connectionReceiver, intentFilter);

        //IntentFilter filter = new IntentFilter();
        //filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        //filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        //registerReceiver(mReceiver, filter);
    }//END ONCREATE

    //於登入頁面按下返回鍵後跳出確認視窗
    /*@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {//捕捉返回鍵
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            ConfirmExit();//按返回鍵，則執行退出確認
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    public void ConfirmExit(){//退出確認
        AlertDialog.Builder ad=new AlertDialog.Builder(AfterLogin.this);
        ad.setTitle("離開");
        ad.setMessage("確定要離開此程式嗎?");
        ad.setPositiveButton("是", new DialogInterface.OnClickListener() {//退出按鈕
            public void onClick(DialogInterface dialog, int i) {
                // TODO Auto-generated method stub
                Log.d("AfterLogin.java", "應用程式關閉");
                WriteLog.appendLog("AfterLogin.java/應用程式關閉");
                AfterLogin.this.finish();//關閉activity
            }
        });
        ad.setNegativeButton("否",new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                //不退出不用執行任何操作
            }
        });
        ad.show();//顯示對話框
    }*/

    /*@Override
    protected void onResume() {
        super.onResume();
        if(BluetoothConnectSetting.connectedBluetoothDevices.size()==0){
            BluetoothStatusTxt.setText("藍牙狀態：未連線");
            BluetoothTicketBtn.setEnabled(false);
        }else{
            BluetoothStatusTxt.setText("藍牙狀態：已連線");
            BluetoothTicketBtn.setEnabled(true);
        }
    }

    //檢查網路是否連線
    public boolean checkInternetConnect(){
        ConnectivityManager cManager = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        boolean isWifi=cManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnectedOrConnecting();
        return isWifi;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(connectionReceiver);
        unregisterReceiver(mReceiver);
        //Toast.makeText(AfterLogin.this, "onDestroy", Toast.LENGTH_SHORT).show();
    }

    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = connectMgr.getActiveNetworkInfo();
            if (mNetworkInfo != null) {
                OnlineTicketBtn.setEnabled(true);
                OfflineImportBtn.setEnabled(true);
                OfflineExportBtn.setEnabled(true);
                InternetStatusTxt.setText("已連線");
                InternetStatusTxt.setTextColor(Color.parseColor("#4caf50"));
                //Toast.makeText(AfterLogin.this, "已連線", Toast.LENGTH_SHORT).show();
            }else{
                OnlineTicketBtn.setEnabled(false);
                OfflineImportBtn.setEnabled(false);
                OfflineExportBtn.setEnabled(false);
                InternetStatusTxt.setText("未連線");
                InternetStatusTxt.setTextColor(Color.RED);
                Toast.makeText(AfterLogin.this, "連線中斷", Toast.LENGTH_SHORT).show();
            }
        }
    };

    //The BroadcastReceiver that listens for bluetooth broadcasts
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothConnectSetting.connectedBluetoothDevices.remove(device);
                Toast.makeText(AfterLogin.this, "藍牙連線關閉", Toast.LENGTH_SHORT).show();
                BluetoothStatusTxt.setText("藍牙狀態：未連線");
            }
        }
    };*/
}

