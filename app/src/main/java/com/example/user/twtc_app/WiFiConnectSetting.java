package com.example.user.twtc_app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by USER on 2015/12/15.
 */
public class WiFiConnectSetting extends Activity {
    String IP;
    EditText IPTxt;
    ConnectionClass Connection;
    private MyDBHelper mydbHelper;
    Button settingBtn,ReturnBtn;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.wifi_connect_setting);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.titlebar);

        //SQLITE
        mydbHelper = new MyDBHelper(this);
        Connection = new ConnectionClass();

        IPTxt = (EditText)findViewById(R.id.IPTxt);
        settingBtn=(Button)findViewById(R.id.SettingBtn);
        ReturnBtn=(Button)findViewById(R.id.ReturnBtn);

        //如果資料庫內先前已設定IP，則抓出來顯示在IP欄位上
        if(mydbHelper.GetConnectIP()!=null){
            IPTxt.setText(mydbHelper.GetConnectIP().trim());
        }

        settingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IP = IPTxt.getText().toString();
                if(isIP(IP)){
                    mydbHelper.InsertToConnectIP(IP);
                    Toast.makeText(WiFiConnectSetting.this, "IP設定完成", Toast.LENGTH_SHORT).show();
                    Log.d("WiFiConnectSetting.java", "IP設定完成");
                    WriteLog.appendLog("WiFiConnectSetting.java/IP設定完成");
                    finish();
                }
                else{
                    Toast.makeText(WiFiConnectSetting.this, "請輸入正確的IP", Toast.LENGTH_SHORT).show();
                    Log.d("WiFiConnect.java/IP", "錯誤的IP"  );
                }
            }
        });//END BTNLOGIN

        ReturnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    /**
          * 判斷IP格式和範圍
         */
    public boolean isIP(String addr) {
        if (addr.length() < 7 || addr.length() > 15 || "".equals(addr)) {
            return false;
        }
        String rexp = "([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}";
        Pattern pat = Pattern.compile(rexp);
        Matcher mat = pat.matcher(addr);
        boolean isipAddress = mat.find();
        return isipAddress;
    }
}
