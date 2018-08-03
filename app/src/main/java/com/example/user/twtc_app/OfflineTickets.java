package com.example.user.twtc_app;

import android.app.Activity;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.security.spec.AlgorithmParameterSpec;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by USER on 2015/11/19.
 */
public class OfflineTickets extends Activity {
    private static final String key="SET31275691$00000000000000000000";
    TextView ResultTxt,ResultTxt2;
    String result="",DEVICE_ID,SPS_ID,TICKET_NO,TK_CODE;
    Button ReturnBtn;
    LinearLayout FailedLayout;

    //剪貼簿
    private ClipboardManager cbMgr;
    private ClipboardManager.OnPrimaryClipChangedListener mPrimaryClipChangedListener;
    //SQLITE
    private MyDBHelper mydbHelper;
    XmlHelper xmlHelper;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        //requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.offline_tickets);
        //getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.titlebar);

        xmlHelper=new XmlHelper(getFilesDir()+"//connectData.xml");

        //接收上個頁面傳來的值
        Intent intent = getIntent();
        DEVICE_ID=intent.getStringExtra("DEVICE_ID");
        SPS_ID = intent.getStringExtra("SPS_ID");

        ReturnBtn=(Button)findViewById(R.id.ReturnBtn);
        ResultTxt=(TextView) findViewById(R.id.ResultTxt);
        ResultTxt2=(TextView) findViewById(R.id.ResultTxt2);
        FailedLayout=(LinearLayout) findViewById(R.id.FailedLayout);

        //SQLITE
        mydbHelper = new MyDBHelper(this);

        //掃描事件
        this.cbMgr=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        mPrimaryClipChangedListener=new ClipboardManager.OnPrimaryClipChangedListener() {
            public void onPrimaryClipChanged() {
                try {
                    //Toast.makeText(OfflineTickets.this, "OfflineTickets", Toast.LENGTH_SHORT).show();
                    setVibrate(100);
                    String qr = cbMgr.getPrimaryClip().getItemAt(0).getText().toString();
                    String a = qr.substring(0, qr.length() - 16);
                    String iv = qr.substring(qr.length() - 16);
                    byte[] descryptBytes = decryptAES(iv.getBytes("UTF-8"), key.getBytes("UTF-8"), Base64.decode(a, Base64.DEFAULT));
                    String getdata = new String(descryptBytes);
                    TICKET_NO = getdata.split("@")[4];
                    TK_CODE = getdata.split("@")[5];
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                    SimpleDateFormat df2 = new SimpleDateFormat("yyyyMMddHHmmss");
                    SimpleDateFormat df3 = new SimpleDateFormat("yyyy-MM-dd");
                    Calendar c = Calendar.getInstance();
                    String[] ResultArray = new String[10];
                    if (1==1) {
                        FailedLayout.setVisibility(View.VISIBLE);
                        setResultText(result = "票券狀態    ");
                        setResultText2(result = "此票券已入場！");
                        ResultTxt2.setTextColor(Color.RED);
                        //setResultText(result = "票券狀態：驗票失敗，此票券已入場" + "\n\n票券號碼：" + TICKET_NO + "\n\n票券種類：" + mydbHelper.GetTKName(TK_CODE) + "\n\n票券入場紀錄：" + mydbHelper.selectUltraLight03ENTER_DT(TICKET_NO, SPS_ID));
                        //ResultTxt.setTextColor(Color.RED);
                    } else {
                        FailedLayout.setVisibility(View.GONE);
                        setResultText(result = "票券狀態    驗票成功" + "\n\n票券號碼    " + TICKET_NO + "\n\n票券種類    " + "" + "\n\n票券入場紀錄\n\n" + df.format(c.getTime()));
                        //ResultTxt.setTextColor(Color.BLUE);
                        ResultArray[0] = "A";
                        ResultArray[1] = TICKET_NO;
                        ResultArray[2] = SPS_ID;
                        ResultArray[3] = "I";
                        ResultArray[4] = DEVICE_ID;
                        ResultArray[5] = TK_CODE;
                        ResultArray[6] = qr;
                        ResultArray[7] = df2.format(c.getTime());
                        ResultArray[8] = "";
                        ResultArray[9] = getDateTime();
                        //mydbHelper.InsertToSQLiteUltraLight03(ResultArray, "");
                    }
                    //Toast.makeText(OfflineTickets.this,new String(descryptBytes), Toast.LENGTH_SHORT).show();
                } catch (Exception ex) {
                    FailedLayout.setVisibility(View.VISIBLE);
                    setResultText(result = "票券狀態    ");
                    setResultText2(result = "非花博票券條碼！");
                    ResultTxt2.setTextColor(Color.RED);
                    //Toast.makeText(OfflineTickets.this, "非花博票券條碼", Toast.LENGTH_SHORT).show();
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
    }//END ONCREATE

    @Override
    protected void onPause() {
        super.onPause();
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
    }

    //QRCODE解碼
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

    //票券狀態文字
    private void setResultText(String text) {
        ResultTxt.setText(text);
    }

    //票券狀態文字
    private void setResultText2(String text) {
        ResultTxt2.setText(text);
    }

    //取得現在時間
    public String getDateTime(){
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Calendar c = Calendar.getInstance();
        String str = df.format(c.getTime());
        return str;
    }

    //震動
    public void setVibrate(int time){
        Vibrator myVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        myVibrator.vibrate(time);
    }
}
