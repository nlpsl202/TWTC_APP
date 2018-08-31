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
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Created by Jeff.
 */
public class OfflineExport extends Activity {
    private Button ImportBtn, ReturnBtn,homeBtn;
    private TextView ResultTxt, ResultTxt2;
    private MyDBHelper mydbHelper;
    private XmlHelper xmlHelper;
    private Dialog alertDialog;

    private Connection DBCDPSConnection() {
        ConnectionClass.ip = xmlHelper.ReadValue("ServerIP");
        ConnectionClass.db = "TWTC_CDPS";
        ConnectionClass.un = xmlHelper.ReadValue("sa");
        ConnectionClass.password = xmlHelper.ReadValue("SQLPassWord");
        Connection con = ConnectionClass.CONN();
        return con;
    }

    private Connection DBExhibitConnection() {
        ConnectionClass.ip = xmlHelper.ReadValue("VMSQlIP");
        ConnectionClass.db = "TWTC_Exhibit";
        ConnectionClass.un = xmlHelper.ReadValue("VMSQlsa");
        ConnectionClass.password = xmlHelper.ReadValue("VMSQlpass");
        Connection con = ConnectionClass.CONN();
        return con;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.offline_export);

        ReturnBtn = (Button) findViewById(R.id.ReturnBtn);
        ImportBtn = (Button) findViewById(R.id.ImportBtn);
        homeBtn = (Button) findViewById(R.id.HomeBtn);
        ResultTxt = (TextView) findViewById(R.id.ResultTxt);
        ResultTxt2 = (TextView) findViewById(R.id.ResultTxt2);

        mydbHelper = new MyDBHelper(this);
        xmlHelper = new XmlHelper(getFilesDir() + "//connectData.xml");

        ImportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkInternetConnect()) {
                    final String EL = xmlHelper.ReadValue("NameCode");
                    //取得對應資料表
                    final Cursor cSql = mydbHelper.SelectFromBarcodeLog();
                    final Cursor cWorkSql = mydbHelper.SelectFromWorkCardLog();
                    final int iTotalCount = cSql.getCount() + cWorkSql.getCount();
                    if (iTotalCount > 0) {
                        alertDialog = new Dialog(OfflineExport.this);
                        alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                        alertDialog.setContentView(R.layout.offline_export_alert);
                        alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                        Button a = (Button) alertDialog.findViewById(R.id.ConfirmBtn);
                        a.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    //上傳參觀離線資料
                                    if (cSql.getCount() > 0 && cSql != null) {
                                        if (!UpdateOfflineRecord(cSql, EL)) {
                                            ResultTxt.setText("無法上傳！");
                                            ResultTxt2.setText("上傳失敗，請再次嘗試。");
                                            alertDialog.cancel();
                                        }
                                    }

                                    //上傳服務離線資料
                                    if (cWorkSql.getCount() > 0 && cWorkSql != null) {
                                        if (UpdateOfflineCardPassRecord(cWorkSql, EL) == false) {
                                            ResultTxt.setText("無法上傳！");
                                            ResultTxt2.setText("上傳失敗，請再次嘗試。" + iTotalCount + " 筆！");
                                            return;
                                        }
                                    }

                                    ResultTxt.setText("上傳成功！");
                                    ResultTxt2.setText("上傳完畢，共上傳 " + iTotalCount + " 筆數據。");
                                } catch (Exception e) {
                                    ResultTxt.setText("無法上傳！");
                                    WriteLog.appendLog("OfflineExport.java/upload/Exception:" + e.toString());
                                }
                                alertDialog.cancel();
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
                    } else {
                        ResultTxt.setText("無法上傳！");
                        ResultTxt2.setText("目前無離線驗票資料！");
                    }
                }
            }
        });

        ReturnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OfflineExport.this.finish();
            }
        });

        //Home
        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OfflineExport.this.finish();
            }
        });

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(connectionReceiver);
    }

    //上傳離線參觀證資料
    private boolean UpdateOfflineRecord(Cursor c, String ELCode) {
        //生成物件
        Connection con = DBExhibitConnection();
        try {
            UUID u = UUID.randomUUID();
            boolean Revc = false;
            CallableStatement cstmt = con.prepareCall("{ call dbo.SP_UpdateOfflinePassRecord(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
            while (c.moveToNext()) {
                try {
                    if (!c.getString(c.getColumnIndex("EL_Code")).trim().equals(ELCode)) {
                        continue;//錯誤展覽代碼，繼續下筆資料
                    }
                    //刪除空白資料
                    if (c.getString(c.getColumnIndex("EL_Code")).trim().equals("")) {
                        mydbHelper.DeleteOfflineData("BarcodeLog", c.getString(c.getColumnIndex("Rec")));
                        //this.DisplayTransStatus(TotalCount, (NowCount += 1), ((NowCount += 1) >= TotalCount ? false : true));
                        continue;
                    }

                    u = UUID.fromString(c.getString(c.getColumnIndex("SysCode")));

                    cstmt.setString("DeviceID", c.getString(c.getColumnIndex("DeviceID")).trim());
                    cstmt.setString("DirectionType", c.getString(c.getColumnIndex("DirectionType")).trim());
                    cstmt.setString("SensorCode", c.getString(c.getColumnIndex("SensorCode")).trim());
                    cstmt.setString("SysCode", u.toString());
                    cstmt.setString("EL_Code", c.getString(c.getColumnIndex("Current_EL_Code")).trim());
                    cstmt.setString("BT_TypeID", c.getString(c.getColumnIndex("BT_TypeID")).trim());
                    cstmt.setString("VP_ValidDateRule", c.getString(c.getColumnIndex("VP_ValidDateRule")).trim());

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                    //判斷允許入場日期
                    if (!c.getString(c.getColumnIndex("VP_ValidDateBegin")).trim().equals("")) {
                        Date NewDateB = sdf.parse(c.getString(c.getColumnIndex("VP_ValidDateBegin")).trim());
                        cstmt.setDate("VP_ValidDateBegin", new java.sql.Date(NewDateB.getTime()));
                    } else {
                        cstmt.setDate("VP_ValidDateBegin", null);
                    }

                    if (!c.getString(c.getColumnIndex("VP_ValidDateEnd")).trim().equals("")) {
                        Date NewDateB = sdf.parse(c.getString(c.getColumnIndex("VP_ValidDateEnd")).trim());
                        cstmt.setDate("VP_ValidDateEnd", new java.sql.Date(NewDateB.getTime()));
                    } else {
                        cstmt.setDate("VP_ValidDateEnd", null);
                    }
                    cstmt.setString("VP_ValidTimeRule", c.getString(c.getColumnIndex("VP_ValidTimeRule")).trim());
                    cstmt.setString("VP_ValidTimeBegin", c.getString(c.getColumnIndex("VP_ValidTimeBegin")).trim());
                    cstmt.setString("VP_ValidTimeEnd", c.getString(c.getColumnIndex("VP_ValidTimeEnd")).trim());
                    cstmt.setString("VP_UseAreaAssign", c.getString(c.getColumnIndex("VP_UseAreaAssign")).trim());
                    cstmt.setString("VP_UsageTimeType", c.getString(c.getColumnIndex("VP_UsageTimeType")).trim());
                    cstmt.setString("VP_UsageTimeTotal", c.getString(c.getColumnIndex("VP_UsageTimeTotal")).trim());
                    cstmt.setString("VP_UsageTimePerDay", c.getString(c.getColumnIndex("VP_UsageTimePerDay")).trim());
                    cstmt.setString("IV_CheckCode", c.getString(c.getColumnIndex("IV_CheckCode")).trim());
                    cstmt.setString("IV_CheckCode2", c.getString(c.getColumnIndex("IV_CheckCode2")).trim());
                    cstmt.setString("IsFuncCar", "N");
                    cstmt.setString("SenseDT", c.getString(c.getColumnIndex("SenseDT")).trim());
                    cstmt.setString("Result", c.getString(c.getColumnIndex("Result")).trim());
                    cstmt.registerOutParameter("ReturnMsg", java.sql.Types.VARCHAR);
                } catch (Exception ex) {//資料錯誤刪除並繼續

                    mydbHelper.DeleteOfflineData("BarcodeLog", c.getString(c.getColumnIndex("Rec")));
                    //this.DisplayTransStatus(TotalCount, (NowCount += 1), ((NowCount += 1) >= TotalCount ? false : true));
                    continue;
                }
                cstmt.execute();

                if (cstmt.getString(22) != null) {
                    String SourceText = cstmt.getString(22).trim();
                    Revc = SourceText.contains("成功");

                    if (!Revc) {
                        //MessageBox.Show("SP_UpdateOfflinePassRecord\r\n回傳訊息，上傳失敗。", "系統提示");
                        return false;
                    } else//刪除該筆資訊
                    {
                        mydbHelper.DeleteOfflineData("BarcodeLog", c.getString(c.getColumnIndex("Rec")));

                        //更新顯示進度條
                        //this.DisplayTransStatus(TotalCount, (NowCount += 1), ((NowCount += 1) >= TotalCount ? false : true));
                    }
                } else {
                    //MessageBox.Show("SP_UpdateOfflinePassRecord\r\n無法取得回傳訊息，上傳失敗。", "系統提示");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            Log.d("AL.java/CheckOnline", e.toString());
            WriteLog.appendLog("OfflineExport.java/CheckOnline/Exception:" + e.toString());
            return false;
        } finally {//釋放物件
            try {
                con.close();
            } catch (Exception ex) {
                WriteLog.appendLog("OfflineExport.java/con.close()/Exception:" + ex.toString());
            }
        }
    }

    private boolean UpdateOfflineCardPassRecord(Cursor c, String ELCode) {
        //生成物件
        Connection con = DBCDPSConnection();
        try {
            boolean Revc = false;
            CallableStatement cstmt = con.prepareCall("{ call dbo.SP_UpdateOfflineCardPassRecord(?,?,?,?,?,?,?,?)}");
            while (c.moveToNext()) {
                try {
                    if (!c.getString(c.getColumnIndex("EL_Code")).trim().equals(ELCode)) {
                        continue;//錯誤展覽代碼，繼續下筆資料
                    }

                    //刪除空白資料
                    if (c.getString(c.getColumnIndex("EL_Code")).trim().equals("")) {
                        mydbHelper.DeleteOfflineData("WorkCardLog", c.getString(c.getColumnIndex("Rec")));
                        //this.DisplayTransStatus(TotalCount, (NowCount += 1), ((NowCount += 1) >= TotalCount ? false : true));
                        continue;
                    }

                    cstmt.setString("DeviceID", c.getString(c.getColumnIndex("DeviceID")).trim());
                    cstmt.setString("DirectionType", c.getString(c.getColumnIndex("DirectionType")).trim());
                    cstmt.setString("SensorCode", c.getString(c.getColumnIndex("SensorCode")).trim());
                    cstmt.setString("CardNo", c.getString(c.getColumnIndex("CodeNo")).trim());
                    cstmt.setString("EL_Code", c.getString(c.getColumnIndex("Current_EL_Code")).trim());
                    cstmt.setString("SenseDT", c.getString(c.getColumnIndex("SenseDT")).trim());
                    cstmt.setString("Result", c.getString(c.getColumnIndex("Result")).trim());
                    cstmt.registerOutParameter("ReturnMsg", java.sql.Types.VARCHAR);
                } catch (Exception e) {//資料錯誤刪除並繼續

                    mydbHelper.DeleteOfflineData("WorkCardLog", c.getString(c.getColumnIndex("Rec")));
                    //this.DisplayTransStatus(TotalCount, (NowCount += 1), ((NowCount += 1) >= TotalCount ? false : true));
                    WriteLog.appendLog("OfflineExport.java/UpdateOfflineCardPassRecord/Exception:" + e.toString());
                    continue;
                }
                cstmt.execute();

                if (cstmt.getString(8) != null) {
                    String SourceText = cstmt.getString(8).trim();
                    Revc = SourceText.contains("成功");

                    if (!Revc) {
                        //MessageBox.Show("SP_UpdateOfflinePassRecord\r\n回傳訊息，上傳失敗。", "系統提示");
                        return false;
                    } else//刪除該筆資訊
                    {
                        mydbHelper.DeleteOfflineData("WorkCardLog", c.getString(c.getColumnIndex("Rec")));
                        //更新顯示進度條
                        //this.DisplayTransStatus(TotalCount, (NowCount += 1), ((NowCount += 1) >= TotalCount ? false : true));
                    }
                } else {
                    //MessageBox.Show("SP_UpdateOfflinePassRecord\r\n無法取得回傳訊息，上傳失敗。", "系統提示");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            Log.d("AL.java/CheckOnline", e.toString());
            WriteLog.appendLog("OfflineExport.java/UpdateOfflineCardPassRecord/Exception:" + e.toString());
            return false;
        } finally {//釋放物件
            try {
                con.close();
            } catch (Exception ex) {
                WriteLog.appendLog("OfflineExport.java/con.close()/Exception:" + ex.toString());
            }
        }
    }

    //檢查網路是否連線
    public boolean checkInternetConnect() {
        ConnectivityManager cManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return cManager.getActiveNetworkInfo() != null;
    }

    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = connectMgr.getActiveNetworkInfo();
            if (mNetworkInfo == null) {
                Toast.makeText(OfflineExport.this, "連線中斷", Toast.LENGTH_SHORT).show();
                OfflineExport.this.finish();
            }
        }
    };
}
