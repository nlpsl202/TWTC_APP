package com.example.user.twtc_app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import java.sql.Connection;

/**
 * Created by USER on 2015/11/20.
 */
public class OfflineImport extends Activity {

    Button ImportBtn,ReturnBtn;
    ListView LvData;
    private MyDBHelper mydbHelper;
    SimpleCursorAdapter adapter;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.offline_import);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.titlebar);

        //SQLITE
        mydbHelper = new MyDBHelper(this);
        ReturnBtn=(Button)findViewById(R.id.ReturnBtn);
        ImportBtn=(Button)findViewById(R.id.ImportBtn);
        LvData=(ListView)findViewById(R.id.LvData);
        //SQL SERVER //建立連線
        ConnectionClass connectionClass;
        Connection con;

        ImportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(checkInternetConnect()) {
                    //從網路資料庫匯入UL3至SQLITE
                    if (1 > 0) {
                        //有資料則無法匯入！
                        AlertDialog.Builder alertad = new AlertDialog.Builder(OfflineImport.this);
                        alertad.setTitle("無法匯入");
                        alertad.setMessage("請先將資料匯出！\n如已匯出，請清除資料。");
                        alertad.setPositiveButton("關閉", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int i) {
                            }
                        });
                        alertad.show();//顯示對話框
                    } else {
                        int insertNo=0;//儲存從場站資料庫匯入幾筆數量
                        insertNo=1;
                        AlertDialog.Builder completead = new AlertDialog.Builder(OfflineImport.this);
                        completead.setTitle("成功匯入");
                        completead.setMessage("已匯入今日出入館紀錄" + insertNo + "筆！");
                        WriteLog.appendLog("OfflineImport.java/成功匯入今日出入館紀錄" + insertNo + "筆！");
                        completead.setPositiveButton("關閉", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int i) {
                            }
                        });
                        completead.show();//顯示對話框
                    }
                }
                else{
                    AlertDialog.Builder alertad = new AlertDialog.Builder(OfflineImport.this);
                    alertad.setTitle("無法匯入");
                    alertad.setMessage("請確認網路狀態！");
                    alertad.setPositiveButton("關閉", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                        }
                    });
                    alertad.show();//顯示對話框
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

    void DataShowToList(Cursor cursor)
    {
        if (cursor != null && cursor.getCount() > 0) {

            SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, cursor, new String[]{"_id", "TK_ENTER_DT"}, new int[]{android.R.id.text1,
                    android.R.id.text2});
           // LvData.setAdapter(adapter);
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
    }

    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = connectMgr.getActiveNetworkInfo();
            if (mNetworkInfo == null) {
                Toast.makeText(OfflineImport.this, "連線中斷", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    };
}
