package com.example.user.twtc_app;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.security.spec.AlgorithmParameterSpec;
import java.sql.CallableStatement;
import java.sql.Connection;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by USER on 2015/11/19.
 */
public class OnlineTicketsCheck extends Activity {
    private static String key="SET31275691$00000000000000000000";
    TextView ResultTxt,PeopleNumTxt;
    String result="",DEVICE_ID,SPS_ID;
    Button ReturnBtn;

    //SQL SERVER
    ConnectionClass connectionClass;
    Connection con;

    //SQLITE
    private MyDBHelper mydbHelper;

    //剪貼簿
    private ClipboardManager cbMgr;
    private ClipboardManager.OnPrimaryClipChangedListener mPrimaryClipChangedListener;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        //requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.online_tickets_check);
        //getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.titlebar);

        //取得上個頁面傳來的值
        Intent intent = getIntent();
        DEVICE_ID=intent.getStringExtra("DEVICE_ID");
        SPS_ID=intent.getStringExtra("SPS_ID");

        ResultTxt=(TextView) findViewById(R.id.ResultTxt);
        PeopleNumTxt=(TextView) findViewById(R.id.PeopleNumTxt);
        ReturnBtn=(Button)findViewById(R.id.ReturnBtn);

        //SQLITE
        mydbHelper = new MyDBHelper(this);

        //SQL SERVER
        connectionClass = new ConnectionClass();
        con= connectionClass.CONN();


        //掃描驗票
        cbMgr=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        mPrimaryClipChangedListener =new ClipboardManager.OnPrimaryClipChangedListener(){
            public void onPrimaryClipChanged() {
                try{
                    //Toast.makeText(OnlineTicketsCheck.this, "OnlineTicketsCheck", Toast.LENGTH_SHORT).show();
                    setVibrate(100);
                    String qr=cbMgr.getPrimaryClip().getItemAt(0).getText().toString();
                    String a=qr.substring(0,qr.length()-16);
                    String iv=qr.substring(qr.length() - 16);
                    byte[] descryptBytes=decryptAES(iv.getBytes("UTF-8"),key.getBytes("UTF-8"), Base64.decode(a, Base64.DEFAULT));
                    String getdata = new String(descryptBytes);
                    String TICKET_NO=getdata.split("@")[4];
                    String TK_CODE=getdata.split("@")[5];
                    connectionClass = new ConnectionClass();
                    con= connectionClass.CONN();
                    CallableStatement cstmt = con.prepareCall("{ call dbo.SP_TVM_TicketStateQuery(?,?,?,?,?)}");
                    cstmt.setString(1,TICKET_NO);
                    cstmt.setString(2,qr);
                    cstmt.setString(3,SPS_ID);
                    cstmt.registerOutParameter(4, java.sql.Types.VARCHAR);
                    cstmt.registerOutParameter(5, java.sql.Types.VARCHAR);
                    cstmt.execute();
                    String RETURN_MSG = cstmt.getString(4);
                    String TK_NAME = "";
                    String RETURN_MSG_DATETIME = cstmt.getString(5);
                    cstmt.close();
                    if (RETURN_MSG.indexOf("可入") > -1) {
                        setResultText(result = "票券狀態    " + RETURN_MSG + "\n\n票券號碼    " +TICKET_NO + "\n\n票券種類    " + TK_NAME);
                        //ResultTxt.setTextColor(Color.BLACK);
                    }else if(RETURN_MSG.indexOf("逾時") > -1){
                        setResultText(result = "票券狀態    " + RETURN_MSG);
                        //ResultTxt.setTextColor(Color.RED);
                    }else if(RETURN_MSG.indexOf("已入場") > -1){
                        ResultTxt.setTextColor(Color.BLACK);
                        String text = "票券狀態    "+RETURN_MSG+"\n\n票券號碼    "+TICKET_NO+"\n\n票券種類    "+TK_NAME+"\n\n票券入場紀錄\n\n"+RETURN_MSG_DATETIME;
                        Spannable spannable = new SpannableString(text);
                        spannable.setSpan(new ForegroundColorSpan(Color.RED), 8, 8+RETURN_MSG.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        ResultTxt.setText(spannable, TextView.BufferType.SPANNABLE);
                    }else if(RETURN_MSG.indexOf("作廢") > -1){
                        //setResultText(result = "票券狀態    " + RETURN_MSG);
                        //ResultTxt.setTextColor(Color.RED);
                        ResultTxt.setTextColor(Color.BLACK);
                        String text = "票券狀態    "+RETURN_MSG;
                        Spannable spannable = new SpannableString(text);
                        spannable.setSpan(new ForegroundColorSpan(Color.RED), 8, 8+RETURN_MSG.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        ResultTxt.setText(spannable, TextView.BufferType.SPANNABLE);
                    }else if(RETURN_MSG.indexOf("無此售票") > -1){
                        //setResultText(result = "票券狀態    " + RETURN_MSG);
                        //ResultTxt.setTextColor(Color.RED);
                        ResultTxt.setTextColor(Color.BLACK);
                        String text = "票券狀態    "+RETURN_MSG;
                        Spannable spannable = new SpannableString(text);
                        spannable.setSpan(new ForegroundColorSpan(Color.RED), 8, 8+RETURN_MSG.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        ResultTxt.setText(spannable, TextView.BufferType.SPANNABLE);
                    }else if(RETURN_MSG.indexOf("非法票券") > -1){
                        //setResultText(result = "票券狀態    " + RETURN_MSG);
                        //ResultTxt.setTextColor(Color.RED);
                        ResultTxt.setTextColor(Color.BLACK);
                        String text = "票券狀態    "+RETURN_MSG;
                        Spannable spannable = new SpannableString(text);
                        spannable.setSpan(new ForegroundColorSpan(Color.RED), 8, 8+RETURN_MSG.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        ResultTxt.setText(spannable, TextView.BufferType.SPANNABLE);
                    }
                }catch(Exception ex){
                    ResultTxt.setTextColor(Color.BLACK);
                    String text = "票券狀態    非花博票券條碼！";
                    Spannable spannable = new SpannableString(text);
                    spannable.setSpan(new ForegroundColorSpan(Color.RED), 8, 16, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ResultTxt.setText(spannable, TextView.BufferType.SPANNABLE);
                    //setResultText(result = "票券狀態    非花博票券條碼");
                    //ResultTxt.setTextColor(Color.RED);
                }
            }
        };
        cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);

        ReturnBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);
        //String text = "票券狀態    已入場\n\n票券號碼    IV1234567\n\n票券種類    學生票\n\n票券入場紀錄\n\n2018-01-01 00:00:00.000";
        //Spannable spannable = new SpannableString(text);
        //spannable.setSpan(new ForegroundColorSpan(Color.RED), 8, 11, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        //ResultTxt.setText(spannable, TextView.BufferType.SPANNABLE);
        //ResultTxt.setText("票券狀態    "+Html.fromHtml("<font color='blue'>已入場</font>")+"\n\n奇怪");
        //setResultText(result = "票券狀態    <font color='red'>已入場</font>\n\n票券號碼    IV1234567\n\n票券種類    學生票\n\n票券入場紀錄\n\n2018-01-01 00:00:00.000");
    }//End ON CREATE

    @Override
    protected void onPause() {
        super.onPause();
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(connectionReceiver);
    }

    public static byte[] decryptAES (byte[] ivBytes, byte[] keyBytes,byte[] textBytes) {
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

    private void setResultText(String text) {
        ResultTxt.setText(text);
    }

    //震動
    public void setVibrate(int time){
        Vibrator myVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        myVibrator.vibrate(time);
    }

    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = connectMgr.getActiveNetworkInfo();
            if (mNetworkInfo == null) {
                Toast.makeText(OnlineTicketsCheck.this, "連線中斷", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    };
}