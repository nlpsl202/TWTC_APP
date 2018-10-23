package com.example.user.twtc_app;

import android.app.Activity;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Created by Jeff.
 */
public class WiFiConnectSetting extends Activity {
    public static Activity activity;
    private EditText deviceID;
    private Button connectBtn,connectBtn2, returnBtn, homeBtn;

    //XML
    private XmlHelper xmlHelper;

    //剪貼簿
    private ClipboardManager cbMgr;
    private ClipboardManager.OnPrimaryClipChangedListener mPrimaryClipChangedListener;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.wifi_connect_setting);

        activity=this;

        connectBtn = (Button) findViewById(R.id.ConnectBtn);
        connectBtn2 = (Button) findViewById(R.id.ConnectBtn2);
        returnBtn = (Button) findViewById(R.id.ReturnBtn);
        homeBtn = (Button) findViewById(R.id.homeBtn);
        deviceID = (EditText) findViewById(R.id.deviceID);
        cbMgr = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        xmlHelper = new XmlHelper(getFilesDir() + "//connectData.xml");

        if (deviceID.getText().toString().length()>0){
            connectBtn.setEnabled(true);
            connectBtn2.setEnabled(false);
        }else{
            connectBtn.setEnabled(false);
            connectBtn2.setEnabled(true);
        }

        deviceID.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length()>0){
                    connectBtn.setEnabled(true);
                    connectBtn2.setEnabled(false);
                }else{
                    connectBtn.setEnabled(false);
                    connectBtn2.setEnabled(true);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,int count, int after) {
                if (s.length()>0){
                    connectBtn.setEnabled(true);
                    connectBtn2.setEnabled(false);
                }else{
                    connectBtn.setEnabled(false);
                    connectBtn2.setEnabled(true);
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length()>0){
                    connectBtn.setEnabled(true);
                    connectBtn2.setEnabled(false);
                }else{
                    connectBtn.setEnabled(false);
                    connectBtn2.setEnabled(true);
                }
            }
        });

        connectBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent callSub = new Intent();
                    callSub.setClass(WiFiConnectSetting.this, OnlineTickets.class);
                    callSub.putExtra("DEVICE_ID", deviceID.getText().toString());//傳遞DEVICE_ID給登入後的頁面
                    startActivity(callSub);
                    WiFiConnectSetting.this.finish();
                } catch (Exception ex) {
                    Toast.makeText(WiFiConnectSetting.this, "設定驗票區域失敗!", Toast.LENGTH_SHORT).show();
                    WriteLog.appendLog("WiFiConnctSetting.java/areaConnect/Exception:" + ex.toString());
                }
            }
        });

        mPrimaryClipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
            public void onPrimaryClipChanged() {
                try {
                    setVibrate(100);
                    deviceID.setText("");
                    Intent callSub = new Intent();
                    callSub.setClass(WiFiConnectSetting.this, OnlineTickets.class);
                    callSub.putExtra("DEVICE_ID", cbMgr.getPrimaryClip().getItemAt(0).getText().toString());//傳遞DEVICE_ID給登入後的頁面
                    startActivity(callSub);
                    WiFiConnectSetting.this.finish();
                } catch (Exception ex) {
                    Toast.makeText(WiFiConnectSetting.this, "設定驗票區域失敗!", Toast.LENGTH_SHORT).show();
                    WriteLog.appendLog("WiFiConnctSetting.java/areaConnect/Exception:" + ex.toString());
                }
            }
        };
        cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);

        connectBtn2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent callSub = new Intent();
                    callSub.setClass(WiFiConnectSetting.this, OnlineTickets.class);
                    callSub.putExtra("DEVICE_ID", xmlHelper.ReadValue("MachineID"));//傳遞DEVICE_ID給登入後的頁面
                    startActivity(callSub);
                    //WiFiConnectSetting.this.finish();
                } catch (Exception ex) {
                    Toast.makeText(WiFiConnectSetting.this, "系統預設連線失敗!", Toast.LENGTH_SHORT).show();
                    WriteLog.appendLog("WiFiConnctSetting.java/defaultConnect/Exception:" + ex.toString());
                }
            }
        });

        returnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WiFiConnectSetting.this.finish();
            }
        });

        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WiFiConnectSetting.this.finish();
            }
        });
    }//END ONCREATE

    @Override
    protected void onResume() {
        super.onResume();
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
        cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
    }

    //震動
    public void setVibrate(int time) {
        Vibrator myVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        myVibrator.vibrate(time);
    }
}
