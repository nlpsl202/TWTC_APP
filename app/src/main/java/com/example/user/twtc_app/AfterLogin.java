package com.example.user.twtc_app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.sql.CallableStatement;
import java.sql.Connection;

/**
 * Created by Jeff.
 */
public class AfterLogin extends Activity {
    private Button BluetoothTicketBtn, WifiTicketBtn, OffineTicketBtn, OfflineExportBtn, ConnectSettingBtn;
    private XmlHelper xmlHelper;
    private Dialog alertDialog;

    //連接中央SQL
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
        setContentView(R.layout.after_login);

        BluetoothTicketBtn = (Button) findViewById(R.id.BluetoothTicketBtn);
        WifiTicketBtn = (Button) findViewById(R.id.WifiTicketBtn);
        OffineTicketBtn = (Button) findViewById(R.id.OffineTicketBtn);
        OfflineExportBtn = (Button) findViewById(R.id.OfflineExportBtn);
        ConnectSettingBtn = (Button) findViewById(R.id.ConnectSettingBtn);

        xmlHelper = new XmlHelper(getFilesDir() + "//connectData.xml");

        //切換至藍牙驗票
        BluetoothTicketBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (BluetoothConnectSetting.connectedBluetoothDevices.size() > 0) {
                    Intent callSub = new Intent();
                    callSub.setClass(AfterLogin.this, BluetoothTickets.class);
                    startActivity(callSub);
                } else {
                    Intent callSub = new Intent();
                    callSub.setClass(AfterLogin.this, BluetoothConnectSetting.class);
                    startActivity(callSub);
                }
            }

        });

        //切換至WiFi驗票
        WifiTicketBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pingIP()) {
                    if (CheckOnline()) {
                        Intent callSub = new Intent();
                        callSub.setClass(AfterLogin.this, WiFiConnectSetting.class);
                        startActivity(callSub);
                    } else {
                        Toast.makeText(AfterLogin.this, "設備回報失敗，請確認IP及網路連線是否正確!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(AfterLogin.this, "設備回報失敗，請確認IP及網路連線是否正確!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        //切換至離線驗票
        OffineTicketBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent callSub = new Intent();
                callSub.setClass(AfterLogin.this, OfflineTickets.class);
                startActivity(callSub);
            }
        });

        //切換至離線資料上傳
        OfflineExportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent callSub = new Intent();
                callSub.setClass(AfterLogin.this, OfflineExport.class);
                startActivity(callSub);
            }
        });

        //切換至系統設定
        ConnectSettingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    final EditText pass_et;
                    final File file = new File(getFilesDir() + "//connectData.xml");
                    alertDialog = new Dialog(AfterLogin.this);
                    alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    alertDialog.setContentView(R.layout.connect_setting_alert);
                    alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    pass_et = (EditText) alertDialog.findViewById(R.id.pass_et);
                    Button a = (Button) alertDialog.findViewById(R.id.ConfirmBtn);
                    a.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (file.exists()) {
                                if (!pass_et.getText().toString().equals(xmlHelper.ReadValue("SetupPassWord"))) {
                                    Toast.makeText(AfterLogin.this, "密碼錯誤", Toast.LENGTH_SHORT).show();
                                } else {
                                    alertDialog.cancel();
                                    Intent callSub = new Intent();
                                    callSub.setClass(AfterLogin.this, ConnectSetting.class);
                                    startActivity(callSub);
                                }
                            } else {
                                if (!pass_et.getText().toString().equals("0000")) {
                                    Toast.makeText(AfterLogin.this, "密碼錯誤", Toast.LENGTH_SHORT).show();
                                } else {
                                    alertDialog.cancel();
                                    Intent callSub = new Intent();
                                    callSub.setClass(AfterLogin.this, ConnectSetting.class);
                                    startActivity(callSub);
                                }
                            }
                        }
                    });
                    Button b = (Button) alertDialog.findViewById(R.id.CancelBtn);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            alertDialog.cancel();
                        }
                    });
                    alertDialog.show();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    WriteLog.appendLog("AfterLogin.java/ConnectSettingBtn/Exception:" + ex.toString());
                }
            }
        });

        //監聽網路狀態
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);

        //監聽藍芽狀態
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mReceiver, filter);

        /*WifiManager mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiManager.setWifiEnabled(true);
        int i = mWifiManager.getConnectionInfo().getNetworkId();
        String name = mWifiManager.getConnectionInfo().getSSID();
        mWifiManager.enableNetwork(i, true);
        copyDbToExternal(this);*/
    }//END ONCREATE

    @Override
    protected void onResume() {
        super.onResume();
        if (checkInternetConnect()) {
            WifiTicketBtn.setEnabled(true);
            OfflineExportBtn.setEnabled(true);
        } else {
            WifiTicketBtn.setEnabled(false);
            OfflineExportBtn.setEnabled(false);
        }
    }

    //設備回報
    private boolean CheckOnline() {
        Connection con = DBCDPSConnection();
        if (con == null) {
            return false;
        }
        try {
            CallableStatement cstmt = con.prepareCall("{ call dbo.SP_UpdateDeviceStatus(?,?,?)}");
            cstmt.setString("DeviceID", xmlHelper.ReadValue("MachineID"));
            cstmt.setString("ServiceType", "0001");
            cstmt.registerOutParameter("ReturnMsg", java.sql.Types.VARCHAR);
            cstmt.execute();

            if (cstmt.getString(3).length() != 0) {
                return cstmt.getString(3).trim().contains("成功");
            } else {
                return false;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            WriteLog.appendLog("AfterLogin.java/CheckOnline/Exception:" + ex.toString());
            return false;
        }
    }

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

    //檢查網路是否連線
    public boolean checkInternetConnect() {
        ConnectivityManager cManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return cManager.getActiveNetworkInfo() != null;
    }

    //於登入頁面按下返回鍵後跳出確認視窗
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {//捕捉返回鍵
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            ConfirmExit();//按返回鍵，則執行退出確認
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void ConfirmExit() {//退出確認
        AlertDialog.Builder ad = new AlertDialog.Builder(AfterLogin.this);
        ad.setTitle("離開");
        ad.setMessage("確定要離開此程式嗎?");
        ad.setPositiveButton("是", new DialogInterface.OnClickListener() {//退出按鈕
            public void onClick(DialogInterface dialog, int i) {
                WriteLog.appendLog("MainActivity.java/應用程式關閉");
                AfterLogin.this.finish();//關閉activity
                System.exit(0);
            }
        });
        ad.setNegativeButton("否", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                //不退出不用執行任何操作
            }
        });
        ad.show();//顯示對話框
    }

    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (checkInternetConnect()) {
                WifiTicketBtn.setEnabled(true);
                OfflineExportBtn.setEnabled(true);
                Toast.makeText(AfterLogin.this, "已連線", Toast.LENGTH_SHORT).show();
            } else {
                WifiTicketBtn.setEnabled(false);
                OfflineExportBtn.setEnabled(false);
                Toast.makeText(AfterLogin.this, "連線中斷", Toast.LENGTH_SHORT).show();
            }
        }
    };

    //監聽藍芽連接狀態的廣播
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothConnectSetting.connectedBluetoothDevices.clear();
            }
        }
    };

    //複製db到電腦檢查資料用
    private void copyDbToExternal(Context context) {
        try {
            File sd = Environment.getExternalStorageDirectory();
            File data = Environment.getDataDirectory();

            if (sd.canWrite()) {
                String currentDBPath = "//data//" + context.getApplicationContext().getPackageName() + "//databases//"
                        + "mydata.db";
                String backupDBPath = "mydata.db";
                File currentDB = new File(data, currentDBPath);
                File backupDB = new File(sd, backupDBPath);

                FileChannel src = new FileInputStream(currentDB).getChannel();
                FileChannel dst = new FileOutputStream(backupDB).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

