package com.example.user.twtc_app;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
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
    private Button wifiBtn, connectBtn, returnBtn, homeBtn;
    private String networkSSID;

    //XML
    private XmlHelper xmlHelper;

    //剪貼簿
    private ClipboardManager cbMgr;
    private ClipboardManager.OnPrimaryClipChangedListener mPrimaryClipChangedListener;
    private WifiManager wifiManager;

    private Connection DBCDPSConnection()
    {
        ConnectionClass.ip=xmlHelper.ReadValue("ServerIP");
        ConnectionClass.db="TWTC_CDPS";
        ConnectionClass.un=xmlHelper.ReadValue("sa");
        ConnectionClass.password=xmlHelper.ReadValue("SQLPassWord");
        Connection con = ConnectionClass.CONN();
        return con;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.wifi_connect_setting);

        wifiBtn = (Button) findViewById(R.id.WifiBtn);
        connectBtn = (Button) findViewById(R.id.ConnectBtn);
        returnBtn = (Button) findViewById(R.id.ReturnBtn);
        homeBtn = (Button) findViewById(R.id.HomeBtn);
        ssid = (EditText) findViewById(R.id.SSID);
        cbMgr = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        xmlHelper = new XmlHelper(getFilesDir() + "//connectData.xml");

        if (wifiManager.isWifiEnabled()) {
            wifiBtn.setBackgroundResource(R.drawable.switch_on);
        } else {
            wifiBtn.setBackgroundResource(R.drawable.switch_off);
        }

        //wifi OnOff
        wifiBtn.setOnClickListener(new View.OnClickListener() {
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
        });

        //wifi Connect Button
        connectBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    networkSSID = ssid.getText().toString();
                    connectedWifiDevices = wifiManager.getConfiguredNetworks();
                    if (connectedWifiDevices.size() > 0) {
                        for (WifiConfiguration i : connectedWifiDevices) {
                            if (i.SSID != null && i.SSID.equals("\"" + networkSSID + "\"")) {
                                wifiManager.disconnect();
                                wifiManager.enableNetwork(i.networkId, true);
                                wifiManager.reconnect();
                                if (CheckOnline()) {
                                    Intent callSub = new Intent();
                                    callSub.setClass(WiFiConnectSetting.this, OnlineTickets.class);
                                    startActivity(callSub);
                                    finish();
                                } else {
                                    Toast.makeText(WiFiConnectSetting.this, "設備回報失敗，請確認IP及網路連線是否正確!", Toast.LENGTH_SHORT).show();
                                }
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
                                wifiManager.enableNetwork(i.networkId, true);
                                wifiManager.reconnect();
                                if (CheckOnline())
                                {
                                    Intent callSub = new Intent();
                                    callSub.setClass(WiFiConnectSetting.this, OnlineTickets.class);
                                    startActivity(callSub);
                                    finish();
                                }
                                else
                                {
                                    Toast.makeText(WiFiConnectSetting.this, "設備回報失敗，請確認IP及網路連線是否正確!", Toast.LENGTH_SHORT).show();
                                }
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
                finish();
            }
        });

        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
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

    //設備回報
    private Boolean CheckOnline()
    {
        Connection con = DBCDPSConnection();
        try {
            CallableStatement cstmt = con.prepareCall("{ call dbo.SP_UpdateDeviceStatus(?,?,?)}");
            cstmt.setString("DeviceID",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("ServiceType","0001");
            cstmt.registerOutParameter("ReturnMsg",java.sql.Types.VARCHAR);
            cstmt.execute();

            Boolean Revc = false;

            if (cstmt.getString(3).length() != 0)
            {
                String SourceText = "";
                String ComText = "";

                SourceText = cstmt.getString(3).trim();
                ComText = "成功";

                Revc = SourceText.contains(ComText);

                return Revc;
            }
            else
            {
                return false;
            }
        }
        catch (Exception e) {
            Log.d("AL.java/CheckOnline", e.toString());
            WriteLog.appendLog("AfterLogin.java/CheckOnline/Exception:" + e.toString());
            return false;
        }
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
                    wifiBtn.setBackgroundResource(R.drawable.switch_on);
                    wifiBtn.setClickable(true);
                    break;
                case WifiManager.WIFI_STATE_ENABLING:
                    wifiBtn.setClickable(false);
                    break;
                case WifiManager.WIFI_STATE_DISABLED:
                    wifiBtn.setBackgroundResource(R.drawable.switch_off);
                    wifiBtn.setClickable(true);
                    break;
                case WifiManager.WIFI_STATE_DISABLING:
                    wifiBtn.setClickable(false);
                    break;
            }
        }
    };
}
