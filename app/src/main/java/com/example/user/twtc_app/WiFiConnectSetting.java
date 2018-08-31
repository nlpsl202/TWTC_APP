package com.example.user.twtc_app;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.util.List;

/**
 * Created by Jeff.
 */
public class WiFiConnectSetting extends Activity {
    private List<WifiConfiguration> connectedWifiDevices;
    private EditText ssid;
    private Button connectBtn, returnBtn, homeBtn;
    private String networkSSID;

    //XML
    private XmlHelper xmlHelper;

    //剪貼簿
    private ClipboardManager cbMgr;
    private ClipboardManager.OnPrimaryClipChangedListener mPrimaryClipChangedListener;
    private WifiManager wifiManager;

    private Handler mThreadHandler,UIhandler;
    private HandlerThread mThread;

    private Connection DBCDPSConnection() {
        ConnectionClass.ip = xmlHelper.ReadValue("ServerIP");
        ConnectionClass.db = "TWTC_CDPS";
        ConnectionClass.un = xmlHelper.ReadValue("sa");
        ConnectionClass.password = xmlHelper.ReadValue("SQLPassWord");
        Connection con = ConnectionClass.CONN();
        return con;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.wifi_connect_setting);

        //wifiBtn = (Button) findViewById(R.id.WifiBtn);
        connectBtn = (Button) findViewById(R.id.ConnectBtn);
        returnBtn = (Button) findViewById(R.id.ReturnBtn);
        homeBtn = (Button) findViewById(R.id.HomeBtn);
        ssid = (EditText) findViewById(R.id.SSID);
        cbMgr = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        xmlHelper = new XmlHelper(getFilesDir() + "//connectData.xml");

        /*if (wifiManager.isWifiEnabled()) {
            wifiBtn.setBackgroundResource(R.drawable.switch_on);
        } else {
            wifiBtn.setBackgroundResource(R.drawable.switch_off);
        }*/

        mThread = new HandlerThread("connectWifi");
        mThread.start();
        mThreadHandler = new Handler(mThread.getLooper());

        UIhandler=new Handler(){
            @Override
            public void handleMessage(Message msg) {
                if(msg.what==0){
                    connectBtn.setEnabled(false);
                }else if(msg.what==1){
                    connectBtn.setEnabled(true);
                }
            }
        };

        //wifi OnOff
        /*wifiBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                wifiBtn.setClickable(false);
                if (wifiManager.isWifiEnabled()) {
                    wifiBtn.setBackgroundResource(R.drawable.switch_off);
                    wifiManager.setWifiEnabled(false);
                } else {
                    wifiBtn.setBackgroundResource(R.drawable.switch_on);
                    wifiManager.setWifiEnabled(true);
                }
            }
        });*/

        //wifi Connect Button
        connectBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    Intent callSub = new Intent();
                    callSub.setClass(WiFiConnectSetting.this, OnlineTickets.class);
                    callSub.putExtra("BSSID", wifiManager.getConnectionInfo().getBSSID());//傳遞DEVICE_ID給登入後的頁面
                    startActivityForResult(callSub, 0);
                    WiFiConnectSetting.this.finish();
                    networkSSID = ssid.getText().toString();
                    connectedWifiDevices = wifiManager.getConfiguredNetworks();
                    if (connectedWifiDevices.size() > 0) {
                        for (WifiConfiguration i : connectedWifiDevices) {
                            if (i.SSID != null && i.SSID.equals("\"" + networkSSID + "\"")) {
                                wifiManager.disconnect();
                                Thread.sleep(100);
                                wifiManager.enableNetwork(i.networkId, true);
                                mThreadHandler.post(connectWifi);
                            }
                        }
                    } else {
                        Toast.makeText(WiFiConnectSetting.this, "未找到已儲存的wifi！", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception ex) {
                    Toast.makeText(WiFiConnectSetting.this, "連接wifi失敗", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mPrimaryClipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
            public void onPrimaryClipChanged() {
                try {
                    setVibrate(100);
                    ssid.setText("");
                    networkSSID = cbMgr.getPrimaryClip().getItemAt(0).getText().toString();
                    connectedWifiDevices = wifiManager.getConfiguredNetworks();
                    if (connectedWifiDevices.size() > 0) {
                        for (WifiConfiguration i : connectedWifiDevices) {
                            if (i.SSID != null && i.SSID.equals("\"" + networkSSID + "\"")) {
                                wifiManager.disconnect();
                                Thread.sleep(100);
                                wifiManager.enableNetwork(i.networkId, true);
                                mThreadHandler.post(connectWifi);
                            }
                        }
                    } else {
                        Toast.makeText(WiFiConnectSetting.this, "未找到已儲存的wifi！", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception ex) {
                    Toast.makeText(WiFiConnectSetting.this, "連接wifi失敗", Toast.LENGTH_SHORT).show();
                }
            }
        };
        cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);

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

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    private Runnable connectWifi = new Runnable() {
        public void run() {
            try {
                cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
                UIhandler.sendEmptyMessage(0);

                while (!checkInternetConnect()) {}//wait wifi connect
                Thread.sleep(100);
                //if (pingIP()) {
                if(CheckOnline()){
                    Intent callSub = new Intent();
                    callSub.setClass(WiFiConnectSetting.this, OnlineTickets.class);
                    callSub.putExtra("BSSID", wifiManager.getConnectionInfo().getBSSID());//傳遞DEVICE_ID給登入後的頁面
                    startActivityForResult(callSub, 0);
                    WiFiConnectSetting.this.finish();
                } else {
                    Toast.makeText(WiFiConnectSetting.this, "設備回報失敗，請確認IP及網路連線是否正確!", Toast.LENGTH_SHORT).show();
                }
                cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);
                UIhandler.sendEmptyMessage(1);
            } catch (Exception ex) {
                Toast.makeText(WiFiConnectSetting.this, "連接wifi失敗", Toast.LENGTH_SHORT).show();
            } finally {

                if (mThreadHandler != null) {
                    mThreadHandler.removeCallbacks(connectWifi);
                }
            }
        }
    };

    private boolean pingIP() {
        try {
            Process process = new ProcessBuilder().command("/system/bin/ping", "-c 2", xmlHelper.ReadValue("ServerIP").trim())
                    .redirectErrorStream(true)
                    .start();
            try {
                int status = process.waitFor();
                if (status == 0) {
                    //ping的通就繼續
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                WriteLog.appendLog("AfterLogin.java/ping/Exception:" + e.toString());
                return false;
            } finally {
                // 記得要釋放掉 process
                process.destroy();
            }
        } catch (Exception e) {
            WriteLog.appendLog("AfterLogin.java/ping/Exception:" + e.toString());
            return false;
        }
    }

    //設備回報
    private boolean CheckOnline() {
        Connection con = DBCDPSConnection();
        try {
            CallableStatement cstmt = con.prepareCall("{ call dbo.SP_UpdateDeviceStatus(?,?,?)}");
            cstmt.setString("DeviceID", xmlHelper.ReadValue("MachineID"));
            cstmt.setString("ServiceType", "0001");
            cstmt.registerOutParameter("ReturnMsg", java.sql.Types.VARCHAR);
            cstmt.execute();

            Boolean Revc = false;

            if (cstmt.getString(3).length() != 0) {
                String SourceText = "";
                String ComText = "";

                SourceText = cstmt.getString(3).trim();
                ComText = "成功";

                Revc = SourceText.contains(ComText);

                return Revc;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.d("AL.java/CheckOnline", e.toString());
            WriteLog.appendLog("AfterLogin.java/CheckOnline/Exception:" + e.toString());
            return false;
        }
    }

    //檢查網路是否連線
    public boolean checkInternetConnect() {
        ConnectivityManager cManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return cManager.getActiveNetworkInfo() != null;
    }

    //震動
    public void setVibrate(int time) {
        Vibrator myVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        myVibrator.vibrate(time);
    }

    //wifi狀態改變
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            switch (wifiState) {
                case WifiManager.WIFI_STATE_ENABLED:
                    //wifiBtn.setBackgroundResource(R.drawable.switch_on);
                    //wifiBtn.setClickable(true);
                    break;
                case WifiManager.WIFI_STATE_ENABLING:
                    //wifiBtn.setClickable(false);
                    break;
                case WifiManager.WIFI_STATE_DISABLED:
                    //wifiBtn.setBackgroundResource(R.drawable.switch_off);
                    //wifiBtn.setClickable(true);
                    break;
                case WifiManager.WIFI_STATE_DISABLING:
                    //wifiBtn.setClickable(false);
                    break;
            }
        }
    };
}
