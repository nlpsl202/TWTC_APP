package com.example.user.twtc_app;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.util.UUID;

/**
 * Created by Jeff.
 */
public class AfterLogin  extends Activity {
    Button BluetoothTicketBtn,WifiTicketBtn,OffineTicketBtn,OfflineExportBtn,ConnectSettingBtn;
    XmlHelper xmlHelper;
    Dialog alertDialog;
    UUID u = UUID.randomUUID();

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
        setContentView(R.layout.after_login);

        BluetoothTicketBtn=(Button)findViewById(R.id.BluetoothTicketBtn);
        WifiTicketBtn=(Button)findViewById(R.id.WifiTicketBtn);
        OffineTicketBtn=(Button)findViewById(R.id.OffineTicketBtn);
        OfflineExportBtn=(Button)findViewById(R.id.OfflineExportBtn);
        ConnectSettingBtn=(Button)findViewById(R.id.ConnectSettingBtn);

        xmlHelper=new XmlHelper(getFilesDir()+"//connectData.xml");

        //切換至藍牙驗票
        BluetoothTicketBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(BluetoothConnectSetting.connectedBluetoothDevices.size()>0){
                    Intent callSub = new Intent();
                    callSub.setClass(AfterLogin.this, BluetoothTickets.class);
                    startActivityForResult(callSub, 0);
                }else{
                    Intent callSub = new Intent();
                    callSub.setClass(AfterLogin.this, BluetoothConnectSetting.class);
                    startActivityForResult(callSub, 0);
                }
            }

        });

        //切換至WiFi驗票
        WifiTicketBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //if (CheckOnline())
                //{
                    Intent callSub = new Intent();
                    callSub.setClass(AfterLogin.this, WiFiConnectSetting.class);
                    startActivityForResult(callSub, 0);
                //}
                //else
                //{
                //    Toast.makeText(AfterLogin.this, "設備回報失敗，請確認IP及網路連線是否正確!", Toast.LENGTH_SHORT).show();
                //}
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
                //Intent callSub = new Intent();
                //callSub.setClass(AfterLogin.this, OfflineExport.class);
                //startActivityForResult(callSub, 0);
                alertDialog = new Dialog(AfterLogin.this);
                alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                alertDialog.setContentView(R.layout.offline_export_alert);
                alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                Button a =(Button)alertDialog.findViewById(R.id.ConfirmBtn);
                a.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String EL = xmlHelper.ReadValue("NameCode");
                        Cursor c = null;
                        Cursor c2 = null;
                        int iTotalCount = 0;
                        try
                        {
                            //取得對應資料表
                            //c = mydbHelper.SelectFromBarcodeLog();
                            //c2 = mydbHelper.SelectFromWorkCardLog();
                            iTotalCount = (c.getCount() + c2.getCount());
                            if (iTotalCount > 0)
                            {
                                //上傳參觀離線資料
                                if (c.getCount() > 0 && c != null)
                                {
                                    //if (UpdateOfflineRecord(c, EL) == false)
                                    //{
                                    //    ResultTxt.setText("無法上傳！");
                                    //    ResultTxt2.setText("上傳失敗，請再次嘗試。" + iTotalCount + " 筆！");
                                    //    return;
                                    //}
                                }

                                //上傳服務離線資料
                                if (c2.getCount() > 0 && c2 != null)
                                {
                                    //if (UpdateOfflineCardPassRecord(dtWorkSql, EL) == false)
                                    //{
                                    //ResultTxt.setText("無法上傳！");
                                    //ResultTxt2.setText("上傳失敗，請再次嘗試。" + iTotalCount + " 筆！");
                                    //return;
                                    //}
                                }

                                //ResultTxt.setText("上傳成功！");
                                //ResultTxt2.setText("成功上傳離線驗票資料 " + iTotalCount + " 筆！");
                            }
                            else
                            {
                                //ResultTxt.setText("無法上傳！");
                                //ResultTxt2.setText("目前無離線驗票資料！");
                            }
                        }
                        catch (Exception ex)
                        {
                            //ResultTxt.setText("無法匯出！");
                        }
                                /*int getNumber=0;
                                mydbHelper.SelectFromUltraLight03();
                                getNumber=mydbHelper.GetUltraLight03ExpNmber();
                                ResultTxt.setText("成功匯出！");
                                ResultTxt2.setText("成功上傳離線驗票資料 " + getNumber + " 筆！");
                                //AlertDialog.Builder alertad = new AlertDialog.Builder(OfflineExport.this);
                                //alertad.setTitle("離線資料上傳");
                                //alertad.setMessage("成功上傳離線驗票資料" + getNumber + "筆！");
                                WriteLog.appendLog("OfflineExport.java/成功上傳離線驗票資料" + getNumber + "筆！");
                                //alertad.setPositiveButton("關閉", new DialogInterface.OnClickListener() {
                                //    public void onClick(DialogInterface dialog, int i) { }
                                //});
                                //alertad.show();//顯示對話框
                                mydbHelper.DeleteUltraLight03Exp();//清除已顯示過的匯出紀錄
                                alertDialog.cancel();*/
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

                        /*AlertDialog.Builder alertad = new AlertDialog.Builder(OfflineExport.this);
                        alertad.setTitle("離線資料上傳");
                        alertad.setMessage("是否將離線資料上傳，上傳後將清除出入館紀錄！");
                        alertad.setPositiveButton("是", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int i) {
                                int getNumber=0;
                                mydbHelper.SelectFromUltraLight03();
                                getNumber=mydbHelper.GetUltraLight03ExpNmber();
                                AlertDialog.Builder alertad = new AlertDialog.Builder(OfflineExport.this);
                                alertad.setTitle("離線資料上傳");
                                alertad.setMessage("成功上傳離線驗票資料" + getNumber + "筆！");
                                WriteLog.appendLog("OfflineExport.java/成功上傳離線驗票資料" + getNumber + "筆！");
                                alertad.setPositiveButton("關閉", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int i) { }
                                });
                                alertad.show();//顯示對話框
                                mydbHelper.DeleteUltraLight03Exp();//請除已顯示過的匯出紀錄
                            }
                        });
                        alertad.setNegativeButton("否", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int i) { }
                        });
                        alertad.show();//顯示對話框*/
                //}else{
                //    ResultTxt.setText("無法匯出！");
                //    ResultTxt2.setText("目前無離線驗票資料！");
                        /*AlertDialog.Builder alertad = new AlertDialog.Builder(OfflineExport.this);
                        alertad.setTitle("離線資料上傳");
                        alertad.setMessage("目前無離線驗票資料！");
                        alertad.setPositiveButton("關閉", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int i) { }
                        });
                        alertad.show();//顯示對話框*/
                //}
            }
        });

        //切換至系統設定
        ConnectSettingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    final EditText pass_et;
                    final File file = new File(getFilesDir()+"//connectData.xml");
                    alertDialog = new Dialog(AfterLogin.this);
                    alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    alertDialog.setContentView(R.layout.connect_setting_alert);
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

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);

        u = UUID.fromString("65A93582-9737-47A4-9288-DEAA9415F03C");
        String a = u.toString();
        WifiManager mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiManager.setWifiEnabled(true);
        int i=mWifiManager.getConnectionInfo().getNetworkId();
        String name=mWifiManager.getConnectionInfo().getSSID();
        mWifiManager.enableNetwork(i,true);
    }//END ONCREATE

    @Override
    protected void onResume() {
        super.onResume();
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (mWifi.isConnected()) {
            WifiTicketBtn.setEnabled(true);
            OfflineExportBtn.setEnabled(true);
        }else{
            WifiTicketBtn.setEnabled(false);
            OfflineExportBtn.setEnabled(false);
        }
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

    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = connectMgr.getActiveNetworkInfo();
            if (mNetworkInfo != null) {
                WifiTicketBtn.setEnabled(true);
                OfflineExportBtn.setEnabled(true);
                //Toast.makeText(AfterLogin.this, "已連線", Toast.LENGTH_SHORT).show();
            }else{
                WifiTicketBtn.setEnabled(false);
                OfflineExportBtn.setEnabled(false);
                //Toast.makeText(AfterLogin.this, "連線中斷", Toast.LENGTH_SHORT).show();
            }
        }
    };
}

