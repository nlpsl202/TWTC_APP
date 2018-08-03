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
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.util.UUID;

/**
 * Created by USER on 2015/11/20.
 */
public class OfflineExport extends Activity {
    Button ImportBtn,ReturnBtn;
    TextView ResultTxt,ResultTxt2;
    private MyDBHelper mydbHelper;
    private XmlHelper xmlHelper;
    Dialog alertDialog;

    private Connection DBCDPSConnection()
    {
        ConnectionClass.ip=xmlHelper.ReadValue("ServerIP");
        ConnectionClass.db="TWTC_CDPS";
        ConnectionClass.un=xmlHelper.ReadValue("sa");
        ConnectionClass.password=xmlHelper.ReadValue("SQLPassWord");
        Connection con = ConnectionClass.CONN();
        return con;
    }

    private Connection DBExhibitConnection()
    {
        ConnectionClass.ip=xmlHelper.ReadValue("VMSQlIP");
        ConnectionClass.db="TWTC_Exhibit";
        ConnectionClass.un=xmlHelper.ReadValue("VMSQlsa");
        ConnectionClass.password=xmlHelper.ReadValue("VMSQlpass");
        Connection con = ConnectionClass.CONN();
        return con;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.offline_export);

        ReturnBtn=(Button)findViewById(R.id.ReturnBtn);
        ImportBtn=(Button)findViewById(R.id.ImportBtn);
        ResultTxt=(TextView) findViewById(R.id.ResultTxt);
        ResultTxt2=(TextView) findViewById(R.id.ResultTxt2);

        mydbHelper = new MyDBHelper(this);

        xmlHelper=new XmlHelper(getFilesDir()+"//connectData.xml");

        ImportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(checkInternetConnect()) {
                    //判斷SQLITE內是否有UltraLight03內TRANSFER_STATUS不為OK的資料（待上傳的意思）
                    //if (mydbHelper.GetUltraLight03NOTOKNumber() > 0) {
                        alertDialog = new Dialog(OfflineExport.this);
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
                                    c = mydbHelper.SelectFromBarcodeLog();
                                    c2 = mydbHelper.SelectFromWorkCardLog();
                                    iTotalCount = (c.getCount() + c2.getCount());
                                    if (iTotalCount > 0)
                                    {
                                        //上傳參觀離線資料
                                        if (c.getCount() > 0 && c != null)
                                        {
                                            if (UpdateOfflineRecord(c, EL) == false)
                                            {
                                                ResultTxt.setText("無法上傳！");
                                                ResultTxt2.setText("上傳失敗，請再次嘗試。" + iTotalCount + " 筆！");
                                                return;
                                            }
                                        }

                                        //上傳服務離線資料
                                        if (c2.getCount() > 0 && c2 != null)
                                        {
                                            //if (UpdateOfflineCardPassRecord(dtWorkSql, EL) == false)
                                            //{
                                                ResultTxt.setText("無法上傳！");
                                                ResultTxt2.setText("上傳失敗，請再次嘗試。" + iTotalCount + " 筆！");
                                                return;
                                            //}
                                        }

                                        ResultTxt.setText("上傳成功！");
                                        ResultTxt2.setText("成功上傳離線驗票資料 " + iTotalCount + " 筆！");
                                    }
                                    else
                                    {
                                        ResultTxt.setText("無法上傳！");
                                        ResultTxt2.setText("目前無離線驗票資料！");
                                    }
                                }
                                catch (Exception ex)
                                {
                                    ResultTxt.setText("無法匯出！");
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
                }else
                {
                    /*AlertDialog.Builder alertad = new AlertDialog.Builder(OfflineExport.this);
                    alertad.setTitle("無法匯出");
                    alertad.setMessage("請確認網路狀態！");
                    alertad.setPositiveButton("關閉", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) { }
                    });
                    alertad.show();//顯示對話框*/
                }
            }
        });

        ReturnBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);
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
    }

    //離線驗票上傳
    private Boolean UpdateOfflineRecord(Cursor c , String ELCode)
    {
        Connection con = DBExhibitConnection();
        UUID u = UUID.fromString("");
        Boolean Revc = false;
        try {
            CallableStatement cstmt = con.prepareCall("{ call dbo.SP_UpdateOfflinePassRecord(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
            cstmt.setString("DeviceID",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("DirectionType",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("SensorCode",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("SysCode",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("EL_Code",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("BT_TypeID",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("VP_ValidDateRule",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("VP_ValidDateBegin",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("VP_ValidDateEnd",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("VP_ValidTimeRule",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("VP_ValidTimeBegin",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("VP_ValidTimeEnd",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("VP_UseAreaAssign",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("VP_UsageTimeType",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("VP_UsageTimeTotal",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("VP_UsageTimePerDay",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("IV_CheckCode",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("IV_CheckCode2",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("IsFuncCar",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("SenseDT",xmlHelper.ReadValue("MachineID"));
            cstmt.setString("Result",xmlHelper.ReadValue("MachineID"));
            cstmt.registerOutParameter("ReturnMsg",java.sql.Types.VARCHAR);
            while (c.moveToNext())
            {
                if(c.getString(c.getColumnIndex("EL_Code")).trim() != ELCode)
                {
                    //MessageBox.Show("展覽代號與當前系統不符合，無法上傳\r\n當前展場代碼：" + ELCode + "\r\n離線展場代碼：" + dr["EL_Code"].ToString().Trim(), "系統提示");
                    continue;//錯誤展覽代碼，繼續下筆資料
                }

                //刪除空白資料
                if (c.getString(c.getColumnIndex("EL_Code")).trim().equals(""))
                {
                    this.DeleteOfflineData("BarcodeLog", c.getString(c.getColumnIndex("Rec")));
                    //this.DisplayTransStatus(TotalCount, (NowCount += 1), ((NowCount += 1) >= TotalCount ? false : true));
                    continue;
                }

                u = UUID.nameUUIDFromBytes(c.getString(c.getColumnIndex("SysCode")).trim().getBytes());

                cstmt.setString("DeviceID",c.getString(c.getColumnIndex("DeviceID")));
                cstmt.setString("DirectionType",c.getString(c.getColumnIndex("DirectionType")));
                cstmt.setString("SensorCode",c.getString(c.getColumnIndex("SensorCode")));
                //cstmt.set("SysCode",u);
                cstmt.setString("EL_Code",c.getString(c.getColumnIndex("Current_EL_Code")));
                cstmt.setString("BT_TypeID",c.getString(c.getColumnIndex("BT_TypeID")));
                cstmt.setString("VP_ValidDateRule",c.getString(c.getColumnIndex("VP_ValidDateRule")));

                //判斷允許入場日期
                /*if (c.getString(c.getColumnIndex("VP_ValidDateBegin")).trim() != "")
                {
                    DateTime VP_ValidDateBegin = DateTime.ParseExact(dr["VP_ValidDateBegin"].ToString().Trim(), "yyyyMMdd", null, System.Globalization.DateTimeStyles.AllowWhiteSpaces);
                    comm.Parameters["@VP_ValidDateBegin"].Value = VP_ValidDateBegin;
                }
                else
                {
                    comm.Parameters["@VP_ValidDateBegin"].Value = null;
                }

                if (dr["VP_ValidDateEnd"].ToString().Trim() != "")
                {
                    DateTime VP_ValidDateEnd = DateTime.ParseExact(dr["VP_ValidDateEnd"].ToString().Trim(), "yyyyMMdd", null, System.Globalization.DateTimeStyles.AllowWhiteSpaces);
                    comm.Parameters["@VP_ValidDateEnd"].Value = VP_ValidDateEnd;
                }
                else
                {
                    comm.Parameters["@VP_ValidDateEnd"].Value = null;
                }

                comm.Parameters["@VP_ValidTimeRule"].Value = dr["VP_ValidTimeRule"].ToString().Trim();
                comm.Parameters["@VP_ValidTimeBegin"].Value = dr["VP_ValidTimeBegin"].ToString().Trim();
                comm.Parameters["@VP_ValidTimeEnd"].Value = dr["VP_ValidTimeEnd"].ToString().Trim();
                comm.Parameters["@VP_UseAreaAssign"].Value = dr["VP_UseAreaAssign"].ToString().Trim();
                comm.Parameters["@VP_UsageTimeType"].Value = dr["VP_UsageTimeType"].ToString().Trim();
                comm.Parameters["@VP_UsageTimeTotal"].Value = dr["VP_UsageTimeTotal"].ToString().Trim();
                comm.Parameters["@VP_UsageTimePerDay"].Value = dr["VP_UsageTimePerDay"].ToString().Trim();
                comm.Parameters["@IV_CheckCode"].Value = dr["IV_CheckCode"].ToString().Trim();
                comm.Parameters["@IV_CheckCode2"].Value = dr["IV_CheckCode2"].ToString().Trim();
                comm.Parameters["@IsFuncCar"].Value = "N";
                comm.Parameters["@SenseDT"].Value = DateTime.ParseExact(dr["SenseDT"].ToString().Trim(), "yyyyMMddHHmmssfff", null, System.Globalization.DateTimeStyles.AllowWhiteSpaces);
                comm.Parameters["@Result"].Value = dr["Result"].ToString().Trim(); ;//Fail:C Pass:A*/
            }
        }
        catch (Exception ex)
        {

        }

            //依據傳入資料進行傳送
            /*foreach (DataRow dr in dt.Rows)
            {
                try
                {


                }
                catch (Exception ex)//資料錯誤刪除並繼續
                {
                    MessageBox.Show("UpdateOfflinePassRecord\r\n【上傳資料意外錯誤】\r\n" + ex.Message);

                    this.DeleteOfflineData("BarcodeLog", dr["Rec"].ToString());
                    this.DisplayTransStatus(TotalCount, (NowCount += 1), ((NowCount += 1) >= TotalCount ? false : true));
                    continue;
                }
                //下指令SQL
                comm.ExecuteNonQuery();

                //取值內容判斷
                if (retValParam.Value.ToString().Length != 0)
                {
                    string SourceText = retValParam.Value.ToString().Trim();
                    Revc = SourceText.Contains("成功");

                    if (!Revc)
                    {
                        MessageBox.Show("SP_UpdateOfflinePassRecord\r\n回傳訊息，上傳失敗。", "系統提示");
                        return false;
                    }
                    else//刪除該筆資訊
                    {
                        this.DeleteOfflineData("BarcodeLog", dr["Rec"].ToString());

                        //更新顯示進度條
                        this.DisplayTransStatus(TotalCount, (NowCount += 1), ((NowCount += 1) >= TotalCount ? false : true));
                    }
                }
                else
                {
                    MessageBox.Show("SP_UpdateOfflinePassRecord\r\n無法取得回傳訊息，上傳失敗。", "系統提示");
                    return false;
                }
            } //end foreach
            return true;
        }
        catch (SqlException ex)
        {
            MessageBox.Show("UpdateOfflinePassRecord\r\n【SqlException】\r\n" + ex.Message);
            return false;
        }
        catch (Exception ex)
        {
            MessageBox.Show("UpdateOfflinePassRecord\r\n【Exception】\r\n" + ex.Message);
            return false;
        }
        finally
        {
            //釋放物件
            comm.Connection.Close();
            comm.Dispose();
            conn.Close();
            conn.Dispose();
        }*/
            return true;
    }

    private Boolean DeleteOfflineData(String _table, String _rev)
    {
        Boolean bRec = true;
        try
        {
            if (mydbHelper != null)
            {
                mydbHelper.DeleteOfflineData("Delete From " + _table + " where Rec='" + _rev + "'");
            }
            else
            {
                return false;
            }
        }
        catch (Exception ex)
        {
            return false;
        }

        return bRec;
    }

    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = connectMgr.getActiveNetworkInfo();
            if (mNetworkInfo == null) {
                Toast.makeText(OfflineExport.this, "連線中斷", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    };
}
