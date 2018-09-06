package com.example.user.twtc_app;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.security.spec.AlgorithmParameterSpec;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by Jeff.
 */
public class OnlineTickets extends Activity {
    private TextView resultTxt, resultTxt2, resultTxt3;
    private Button returnBtn, homeBtn;
    private ImageView photoImage;
    private RadioButton inRBtn, outRBtn;
    private LinearLayout failedLayout, wifiLayout, rfidLayout;
    private String strUserTicketName, reMsg, previousBssid;
    private String[] ary;
    private int UDSTime = 30;

    //SQLITE
    private MyDBHelper mydbHelper;

    //剪貼簿
    private ClipboardManager cbMgr;
    private ClipboardManager.OnPrimaryClipChangedListener mPrimaryClipChangedListener;

    //RFID
    private NfcAdapter mNfcAdapter;
    private PendingIntent mPendingIntent;
    private Bitmap bitmap;
    boolean reading =false;

    //wifi狀態監控
    private WifiManager wifiManager;

    private class RFIDBlock4Info {
        public byte bCardType;
        public byte[] bStartDate;
        public byte[] bEndDate;
        public byte[] bCardID;

        public RFIDBlock4Info(byte[] a, byte[] b, byte[] c) {
            bCardType = 0;
            bStartDate = a;
            bEndDate = b;
            bCardID = c;
        }
    }

    private RFIDBlock4Info BlockInfo = new RFIDBlock4Info(new byte[3], new byte[3], new byte[8]);

    //XML
    private XmlHelper xmlHelper;

    // QRCODE 解碼密碼陣列
    private String[] sQRPWDItem = {"", ""};

    //自動回報設備狀態
    private static long mainTimerInterval = 300000; //預設300秒
    private Handler handler = new Handler();

    //連接中央SQL
    private Connection DBCDPSConnection() {
        ConnectionClass.ip = xmlHelper.ReadValue("ServerIP");
        ConnectionClass.db = "TWTC_CDPS";
        ConnectionClass.un = xmlHelper.ReadValue("sa");
        ConnectionClass.password = xmlHelper.ReadValue("SQLPassWord");
        Connection con = ConnectionClass.CONN();
        return con;
    }

    //連接展覽VMSQL
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
        setContentView(R.layout.online_tickets);

        Intent intent = getIntent();
        previousBssid = intent.getStringExtra("BSSID");

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        /*if (checkInternetConnect()) {
            if (!wifiManager.getConnectionInfo().getBSSID().equals(previousBssid)) {
                Toast.makeText(OnlineTickets.this, "連接到不正確的wifi！", Toast.LENGTH_SHORT).show();
                OnlineTickets.this.finish();
            } else {
                if (!CheckOnline()) {
                    Toast.makeText(OnlineTickets.this, "設備回報失敗，請確認IP及網路連線是否正確！", Toast.LENGTH_SHORT).show();
                    OnlineTickets.this.finish();
                    return;
                }
            }
        }*/

        resultTxt = (TextView) findViewById(R.id.ResultTxt);
        resultTxt2 = (TextView) findViewById(R.id.ResultTxt2);
        resultTxt3 = (TextView) findViewById(R.id.ResultTxt3);
        returnBtn = (Button) findViewById(R.id.ReturnBtn);
        homeBtn = (Button) findViewById(R.id.HomeBtn);
        inRBtn = (RadioButton) findViewById(R.id.InRBtn);
        outRBtn = (RadioButton) findViewById(R.id.OutRBtn);
        failedLayout = (LinearLayout) findViewById(R.id.FailedLayout);
        photoImage = (ImageView) findViewById(R.id.FtPhotoImage);
        wifiLayout = (LinearLayout) findViewById(R.id.wifiLayout);
        rfidLayout = (LinearLayout) findViewById(R.id.rfidLayout);
        cbMgr = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        inRBtn.setChecked(true);

        mydbHelper = new MyDBHelper(this);
        xmlHelper = new XmlHelper(getFilesDir() + "//connectData.xml");

        ResultSet rs = DownloadDeviceSetup();
        try {
            while (rs.next()) {
                if (rs.getString("DL_IsInUse").equals("N")) {
                    Toast.makeText(OnlineTickets.this, "此設備停用中，無法操作", Toast.LENGTH_SHORT).show();
                    return;
                }

                String TmpName = rs.getString("EL_Code");
                String worktype = rs.getString("Work_Type");
                if (worktype != null) {
                    if (worktype.trim() != "") {
                        xmlHelper.WriteValue("WorkType", worktype);
                    }
                }

                //取得服務證驗證狀態
                String INCF_Type = DownloadDeviceType();
                if (INCF_Type.trim() != "") {
                    xmlHelper.WriteValue("IDCF", INCF_Type);
                }
                xmlHelper.WriteValue("UDSTime", Integer.toString(UDSTime));

                ResultSet rs2 = DownloadAllExhibition(TmpName);
                while (rs2.next()) {
                    if (rs2.getString("EL_Code").equals(TmpName)) {
                        xmlHelper.WriteValue("VMSQlIP", rs2.getString("EL_DB_IP"));
                        xmlHelper.WriteValue("VMSQlsa", rs2.getString("EL_DB_Account"));
                        xmlHelper.WriteValue("VMSQlpass", rs2.getString("EL_DB_PWD"));
                        xmlHelper.WriteValue("Name", rs2.getString("EL_Name"));
                        xmlHelper.WriteValue("NameCode", TmpName);
                    }
                }

                //向VM取得當前票劵狀態名稱
                ResultSet rs3 = DownloadBadgeType();
                mydbHelper.DeleteBadgeType();
                mydbHelper.InsertToBadgeType(rs3);
            }
        } catch (Exception ex) {
            WriteLog.appendLog("OnlineTicket.java/OnCreate/Exception:" + ex.toString());
        }

        sQRPWDItem = DownloadExhibitionSetup();
        if (sQRPWDItem == null) {
            Toast.makeText(OnlineTickets.this, "無法取得QRCode相關資訊，請重新連線!", Toast.LENGTH_SHORT).show();
            OnlineTickets.this.finish();
        }
        xmlHelper.WriteValue("Key1", sQRPWDItem[0]);
        xmlHelper.WriteValue("Key2", sQRPWDItem[1]);

        //啟動計時Threading
        if (!xmlHelper.ReadValue("UDSTime").equals("") && xmlHelper.ReadValue("UDSTime") != null) {
            mainTimerInterval = (long) (Integer.parseInt(xmlHelper.ReadValue("UDSTime")) * 1000);
        }
        //設定定時要執行的方法
        handler.removeCallbacks(updateTimer);
        //設定Delay的時間
        handler.postDelayed(updateTimer, 5000);

        //掃描事件
        mPrimaryClipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
            public void onPrimaryClipChanged() {
                try {
                    failedLayout.setVisibility(View.GONE);
                    wifiLayout.setVisibility(View.VISIBLE);
                    rfidLayout.setVisibility(View.GONE);
                    setVibrate(100);
                    if (ary != null) ary = new String[ary.length];
                    //if(xmlHelper.ReadValue("WorkType").equals("W")){//服務證驗證狀態只能用RFID驗票
                    //    setResultText(result = "票劵錯誤，無效票卡");
                    //    return;
                    //}

                    String qr = cbMgr.getPrimaryClip().getItemAt(0).getText().toString();

                    //判斷是否為1D條碼
                    if (qr.length() <= 32 && qr.substring(0, 4).toUpperCase().equals("TWTC") || qr.length() == 16) {
                        BarCodeCheck(qr);
                        return;
                    }

                    String value = qr.substring(0, qr.length() - 16);
                    String iv = qr.substring(qr.length() - 16);
                    ary = QRDecod(value, iv).split("@",-1);

                    if (ary.length == 17) {
                        //檢查UUID 8-4-4-4-12
                        String[] sUUID = ary[0].toString().split("-",-1);
                        if (sUUID.length == 5) {
                            if (sUUID[0].length() != 8 || sUUID[1].length() != 4 || sUUID[2].length() != 4 || sUUID[3].length() != 4 || sUUID[4].length() != 12) {
                                failedLayout.setVisibility(View.VISIBLE);
                                setResultText("票券狀態    ");
                                setResultText2("票劵錯誤\r\n(格式)無效票卡");
                                return;
                            }
                        }
                        tickCheck();
                    } else {
                        failedLayout.setVisibility(View.VISIBLE);
                        setResultText("票券狀態    ");
                        setResultText2("票劵錯誤\r\n(格式)無效票卡");
                    }
                } catch (Exception ex) {
                    failedLayout.setVisibility(View.VISIBLE);
                    setResultText("票券狀態    ");
                    setResultText2("票劵錯誤\r\n(格式)無效票卡");
                    WriteLog.appendLog("OnlineTicket.java/qr/Exception:" + ex.toString());
                }
            }
        };
        cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);

        //回上頁
        returnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineTickets.this.finish();
            }
        });

        //Home
        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnlineTickets.this.finish();
            }
        });

        //RFID
        if (xmlHelper.ReadValue("RFID").equals("OPEN")) {
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (mNfcAdapter == null) {
                Toast.makeText(OnlineTickets.this, "此裝置無NFC功能，無法進行RFID驗票！", Toast.LENGTH_SHORT).show();
                return;
            } else {
                if (!mNfcAdapter.isEnabled()) {
                    Toast.makeText(OnlineTickets.this, "NFC功能尚未開啟，請至設定開啟！", Toast.LENGTH_SHORT).show();
                }
            }
            mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                    getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        }

        //註冊網路狀態監聽
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);

        //註冊wifi變換監聽
        IntentFilter intentFilter2 = new IntentFilter();
        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        registerReceiver(broadcastReceiver, intentFilter2);
    }//End ON CREATE

    @Override
    protected void onResume() {
        super.onResume();
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
        cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);
        if (xmlHelper.ReadValue("RFID").equals("OPEN")) {
            mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
        if (xmlHelper.ReadValue("RFID").equals("OPEN")) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(connectionReceiver);
    }

    //RFID感應事件
    @Override
    protected void onNewIntent(Intent intent) {
        getTagInfo(intent);
    }

    //RFID驗票
    private void getTagInfo(Intent intent) {
        if(reading){
            return;
        }
        reading=true;
        String RFData = "";
        String tagNo = "";
        String strCardID = "", strStartDate = "", strEndDate = "", strCardName = "";
        int iBlockCount = 0;
        byte[] pData;

        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        byte[] tagId = tag.getId();
        for (int i = 0; i < tagId.length; i++) {
            if (Integer.toHexString(tagId[i] & 0xFF).length() < 2) {
                tagNo += "0";
            }
            tagNo += Integer.toHexString(tagId[i] & 0xFF).toUpperCase();
        }
        /*MifareUltralight mifare = MifareUltralight.get(tag);
        try {
            mifare.connect();
            byte[] payload = mifare.readPages(1);
            String ss=getHexToString(byte2hex(payload));
        } catch (IOException e) {
        } finally {
            if (mifare != null) {
                try {
                    mifare.close();
                }
                catch (IOException e) {
                }
            }
        }*/
        MifareClassic mfc = MifareClassic.get(tag);

        try {
            mfc.connect();
            byte[][] pDatas=new byte[64][16];
            int block;
            for(int i=0;i<16;i++){
                if(mfc.authenticateSectorWithKeyA(i, MifareClassic.KEY_DEFAULT)){
                    for(int j=0;j<4;j++){
                        block=i*4+j;
                        if(block==0 || block==7 || block==11 || block==15 || block==19){
                            continue;
                        }
                        pDatas[block]=mfc.readBlock(block);
                    }
                }
            }
            //boolean auth = mfc.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT);
            //if (auth) {
                //讀取卡別資訊
                pData = pDatas[1];
                BlockInfo.bCardType = pData[0];
                if (pData[0] == (byte) 0xFC || pData[0] == (byte) 0xFD) { //服務證與臨時證
                    iBlockCount = 1; //僅讀取Block4
                } else if (pData[0] >= 0x01 && pData[0] <= 0x0F) { //一般證別
                    iBlockCount = pData[0];
                } else { //其他證別(Mifare)
                    //清空圖像資訊
                    bitmap = null;
                    photoImage.setImageBitmap(bitmap);

                    if (UpdateImportPass(tagNo, false, "") == true) {
                        setResultText3("票券狀態    " + reMsg + "\n\n票券身分    參觀證\n\n票券名稱    " + strUserTicketName + (inRBtn.isChecked() ? "\n\n票券入場紀錄\n\n" : "\n\n票券出場紀錄\n\n") + getDateTime());
                    } else {
                        setResultText3("票券狀態    " + reMsg + "\n\n票券身分    參觀證\n\n票券名稱    " + strUserTicketName + (inRBtn.isChecked() ? "\n\n票券入場紀錄\n\n" : "\n\n票券出場紀錄\n\n") + getDateTime());
                    }
                    RFData = "";
                }

                for (int iBlock = 4; iBlock < iBlockCount + 4; iBlock++) {
                    //避開 security block
                    if (iBlock == 7 || iBlock == 11 || iBlock == 15 || iBlock == 19) {
                        iBlockCount++;
                        continue;
                    }
                    //if (mfc.authenticateSectorWithKeyA(iBlock / 4, MifareClassic.KEY_DEFAULT)) {
                        pData = pDatas[iBlock];
                        RFData = RFData + getHexToString(byte2hex(pData));

                        if (BlockInfo.bCardType == (byte) 0xFC || BlockInfo.bCardType == (byte) 0xFD) //定義第四區格式
                        {
                            try {
                                System.arraycopy(pData, 0, BlockInfo.bStartDate, 0, 3);
                                System.arraycopy(pData, 3, BlockInfo.bEndDate, 0, 3);
                                System.arraycopy(pData, 6, BlockInfo.bCardID, 0, 8);
                            } catch (Exception e) {
                                WriteLog.appendLog("OnlineTickets.java/DownloadDeviceSetup/Exception:" + e.toString());
                            }
                        }
                    //}
                }

                ary = RFData.split("@",-1);

                if (BlockInfo.bCardType == (byte) 0xFC || BlockInfo.bCardType == (byte) 0xFD) {
                    wifiLayout.setVisibility(View.GONE);
                    rfidLayout.setVisibility(View.VISIBLE);
                    //判斷當前是否允許服務證驗證
                    //if (!xmlHelper.ReadValue("WorkType").equals("W")) {
                    if (!(1 == 1)) {
                        //清空圖像資訊
                        bitmap = null;
                        photoImage.setImageBitmap(bitmap);

                        setResultText3("票券狀態    驗證模式\r\n不允許服務證驗證");
                    } else {
                        if (BlockInfo.bCardType == (byte) 0xFD) { //服務證
                            if (BlockInfo.bCardID[0] == 'A' || BlockInfo.bCardID[0] == 'G' || BlockInfo.bCardID[0] == 'N') {
                                //do nothing
                                strCardName = "服務證";
                            } else {
                                //清空圖像資訊
                                bitmap = null;
                                photoImage.setImageBitmap(bitmap);
                                setResultText3("票券狀態    票劵錯誤\r\n(格式)無效服務證");
                            }
                        }
                        if (BlockInfo.bCardType == (byte) 0xFC) { //臨時證
                            if ((BlockInfo.bCardID[0] == 'A' || BlockInfo.bCardID[0] == 'G' || BlockInfo.bCardID[0] == 'N') &&
                                    (BlockInfo.bCardID[1] == 'D' || BlockInfo.bCardID[1] == 'D' || BlockInfo.bCardID[1] == 'D')) {
                                //do nothing
                                strCardName = "臨時證";
                            } else {
                                //清空圖像資訊
                                bitmap = null;
                                photoImage.setImageBitmap(bitmap);
                                setResultText3("票券狀態    票劵錯誤\r\n(格式)無效臨時證");
                            }
                        }
                        try {
                            //轉換ASCII to ANSI
                            strCardID = new String(BlockInfo.bCardID, "UTF-8");
                            strStartDate = String.format("%04d", 2000 + (BlockInfo.bStartDate[0] & 0xFF)) + String.format("%02d", BlockInfo.bStartDate[1] & 0xFF) + String.format("%02d", BlockInfo.bStartDate[2] & 0xFF);
                            strEndDate = String.format("%04d", 2000 + (BlockInfo.bEndDate[0] & 0xFF)) + String.format("%02d", BlockInfo.bEndDate[1] & 0xFF) + String.format("%02d", BlockInfo.bEndDate[2] & 0xFF);
                            Date date = new Date();
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                            String dateString = sdf.format(date);
                            //判斷當前時間與有效時間
                            if (Integer.parseInt(dateString) < Integer.parseInt(strStartDate) || Integer.parseInt(dateString) > Integer.parseInt(strEndDate)) {
                                //清空圖像資訊
                                bitmap = null;
                                photoImage.setImageBitmap(bitmap);
                                setResultText3("票券狀態    票劵錯誤\r\n(逾期)無效票卡");
                            } else {//向中央取得權限
                                if (UpdateCertiCardPassRecord(tagNo, strCardID, xmlHelper.ReadValue("NameCode")) == true) {
                                    //顯示登入者圖像
                                    if (bitmap != null) {
                                        photoImage.setVisibility(View.VISIBLE);
                                        photoImage.setImageBitmap(bitmap);
                                    }
                                    setResultText3("票券狀態    驗票成功\n\n票券身分    " + strCardName + (inRBtn.isChecked() ? "\n\n票券入場紀錄\n\n" : "\n\n票券出場紀錄\n\n") + getDateTime());
                                } else {
                                    //清空圖像資訊
                                    bitmap = null;
                                    photoImage.setImageBitmap(bitmap);
                                    //photoImage.setVisibility(View.GONE);
                                    if (reMsg.length() > 9) {
                                        setResultText3("票券狀態    \n" + reMsg.substring(0, 9) + "\n" + reMsg.substring(9) + (inRBtn.isChecked() ? "\n\n票券入場紀錄\n\n" : "\n\n票券出場紀錄\n\n") + getDateTime());
                                    } else {
                                        setResultText3("票券狀態    \n" + reMsg + (inRBtn.isChecked() ? "\n\n票券入場紀錄\n\n" : "\n\n票券出場紀錄\n\n") + getDateTime());
                                    }
                                }
                                RFData = "";
                            }
                        } catch (Exception ex) {
                            //清空圖像資訊
                            bitmap = null;
                            photoImage.setImageBitmap(bitmap);
                            setResultText3("票券狀態    票劵錯誤\r\n(異常)請重新確認");
                            reading=false;
                        }
                    }
                } else {
                    //清空圖像資訊
                    bitmap = null;
                    photoImage.setImageBitmap(bitmap);
                    wifiLayout.setVisibility(View.VISIBLE);
                    rfidLayout.setVisibility(View.GONE);
                    if (ary.length == 17) {
                        if (UpdatePassRecord(tagNo, false, ary) == true) {
                            failedLayout.setVisibility(View.GONE);
                            setResultText("票券狀態    " + reMsg + "\n\n票券身分    參觀證\n\n票券名稱    " + mydbHelper.GetBadgeType(ary[2].toString()) + (inRBtn.isChecked() ? "\n\n票券入場紀錄\n\n" : "\n\n票券出場紀錄\n\n") + getDateTime());
                        } else {
                            failedLayout.setVisibility(View.VISIBLE);
                            setResultText("票券狀態    ");
                            setResultText2(reMsg);
                        }
                        RFData = "";
                    } else {
                        failedLayout.setVisibility(View.VISIBLE);
                        setResultText("票券狀態    ");
                        setResultText2("票劵錯誤\r\n(格式)無效票卡");
                    }
                }
            //} else { // Authentication failed - Handle it
            reading=false;
            //}
        } catch (IOException ex) {
            WriteLog.appendLog("OnlineTicket.java/DownloadDeviceType/Exception:" + ex.toString());
        }
    }

    //一維條碼(BARCODE)驗票
    private void BarCodeCheck(String qr) {
        if (UpdateImportPass("", true, qr) == true) {
            failedLayout.setVisibility(View.GONE);
            setResultText("票券狀態    " + reMsg + "\n\n票券身分    參觀證\n\n票券名稱    " + strUserTicketName + (inRBtn.isChecked() ? "\n\n票券入場紀錄\n\n" : "\n\n票券出場紀錄\n\n") + getDateTime());
        } else {
            failedLayout.setVisibility(View.VISIBLE);
            setResultText("票券狀態    ");
            setResultText2(reMsg);
        }
    }

    //二維條碼(QRCODE)驗票
    private void tickCheck() {
        if (UpdatePassRecord(ary[0].toString().trim(), true, ary) == true) {
            failedLayout.setVisibility(View.GONE);
            setResultText("票券狀態    " + reMsg + "\n\n票券身分    參觀證\n\n票券名稱    " + mydbHelper.GetBadgeType(ary[2].toString()) + (inRBtn.isChecked() ? "\n\n票券入場紀錄\n\n" : "\n\n票券出場紀錄\n\n") + getDateTime());
        } else {
            failedLayout.setVisibility(View.VISIBLE);
            setResultText("票券狀態    ");
            setResultText2(reMsg);
        }
    }

    //一維條碼(BARCODE)驗票SP
    private Boolean UpdateImportPass(String guid, Boolean type, String barcode) {
        Connection conUIP = DBExhibitConnection();
        strUserTicketName = "";
        try {
            String RF = "";

            if (guid != "") {
                if (!type)//為true時候代表刷讀票劵，false為RFID
                {
                    String[] guidary = guid.split("-",-1);
                    for (String guidstr : guidary) {
                        RF += guidstr;
                    }
                }
            }
            CallableStatement cstmtUIP = conUIP.prepareCall("{ call dbo.SP_UpdateImportPass(?)}");
            cstmtUIP.setString("DeviceID", xmlHelper.ReadValue("MachineID"));
            cstmtUIP.setString("DirectionType", inRBtn.isChecked() ? "I" : "O");
            cstmtUIP.setString("SensorCode", RF);
            cstmtUIP.setString("BarCode", barcode);
            cstmtUIP.registerOutParameter("ReturnMsg", java.sql.Types.VARCHAR);

            ResultSet rsUIP = cstmtUIP.executeQuery();

            while (rsUIP.next()) {
                ResultSetMetaData rsMetaData = rsUIP.getMetaData();
                int numberOfColumns = rsMetaData.getColumnCount();

                for (int i = 1; i < numberOfColumns + 1; i++) {
                    String columnName = rsMetaData.getColumnName(i);

                    if ("BT_TypeName".equals(columnName)) {
                        if (rsUIP.getString("BT_TypeName") != null) {
                            strUserTicketName = rsUIP.getString("BT_TypeName");
                        }
                    }
                }
            }

            if (cstmtUIP.getString(5) != null) {
                reMsg = cstmtUIP.getString(5).trim();
                return reMsg.contains("成功");
            } else {
                reMsg = "驗票失敗\r\n無法取得回傳訊息";
                return false;
            }
        } catch (Exception ex) {
            reMsg = "驗票失敗\r\n發生無法預期錯誤";
            WriteLog.appendLog("OnlineTicket.java/UpdateImportPass/Exception:" + ex.toString());
            return false;
        } finally {
            try {
                conUIP.close();
            } catch (Exception ex) {
                WriteLog.appendLog("OnlineTicket.java/conUIP.close()/Exception:" + ex.toString());
                return false;
            }
        }
    }

    //二維條碼(QRCODE)驗票SP
    private Boolean UpdatePassRecord(String guid, Boolean type, String[] QRarray) {
        Connection conUPR = DBExhibitConnection();
        try {
            UUID u = UUID.randomUUID();
            u = UUID.nameUUIDFromBytes(QRarray[0].getBytes());

            String RF = "";

            if (type == false) {//為true時候代表刷讀票劵，false為RFID
                RF = guid;
            }

            CallableStatement cstmtUPR = conUPR.prepareCall("{ call dbo.SP_UpdatePassRecord(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
            cstmtUPR.setString("DeviceID", xmlHelper.ReadValue("MachineID"));
            cstmtUPR.setString("DirectionType", inRBtn.isChecked() ? "I" : "O");
            cstmtUPR.setString("SensorCode", RF);
            cstmtUPR.setString("SysCode", u.toString());
            //cstmtUPR.setString("EL_Code",QRarray[1].toString().trim());
            cstmtUPR.setString("EL_Code", "TE2017");
            cstmtUPR.setString("BT_TypeID", QRarray[2].toString().trim());
            cstmtUPR.setString("VP_ValidDateRule", QRarray[3].toString().trim());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            if (QRarray[4].toString().trim() != "") {
                Date NewDateB = sdf.parse(QRarray[4].toString().trim());
                cstmtUPR.setDate("VP_ValidDateBegin", new java.sql.Date(NewDateB.getTime()));
            } else {
                cstmtUPR.setDate("VP_ValidDateBegin", null);
            }
            if (QRarray[5].toString().trim() != "") {
                Date NewDateE = sdf.parse(QRarray[5].toString().trim());
                cstmtUPR.setDate("VP_ValidDateEnd", new java.sql.Date(NewDateE.getTime()));
            } else {
                cstmtUPR.setDate("VP_ValidDateEnd", null);
            }
            cstmtUPR.setString("VP_ValidTimeRule", QRarray[6].toString().trim());
            //cstmtUPR.setString("VP_ValidTimeBegin",QRarray[7].toString().trim());
            cstmtUPR.setString("VP_ValidTimeBegin", "20180101");
            //cstmtUPR.setString("VP_ValidTimeEnd",QRarray[8].toString().trim());
            cstmtUPR.setString("VP_ValidTimeEnd", "20181231");
            cstmtUPR.setString("VP_UseAreaAssign", QRarray[9].toString().trim());
            //cstmtUPR.setString("VP_UsageTimeType",QRarray[10].toString().trim());
            cstmtUPR.setString("VP_UsageTimeType", "0");
            cstmtUPR.setString("VP_UsageTimeTotal", QRarray[11].toString().trim());
            cstmtUPR.setString("VP_UsageTimePerDay", QRarray[12].toString().trim());
            cstmtUPR.setString("IV_CheckCode", QRarray[15].toString().trim());
            cstmtUPR.setString("IV_CheckCode2", QRarray[16].toString().trim());
            cstmtUPR.setString("IsFuncCar", "N");
            cstmtUPR.registerOutParameter("ReturnMsg", java.sql.Types.VARCHAR);
            cstmtUPR.execute();
            if (cstmtUPR.getString(20) != null) {
                reMsg = cstmtUPR.getString(20).trim();
                return reMsg.contains("成功");
            } else {
                reMsg = "驗票失敗\r\n無法取得回傳訊息";
                return false;
            }
        } catch (Exception ex) {
            reMsg = "驗票失敗\r\n發生無法預期錯誤";
            WriteLog.appendLog("OnlineTicket.java/UpdatePassRecord/Exception:" + ex.toString());
            return false;
        } finally {
            try {
                conUPR.close();
            } catch (Exception ex) {
                WriteLog.appendLog("OnlineTicket.java/conUPR.close()/Exception:" + ex.toString());
            }
        }
    }

    //RFID驗票SP
    private Boolean UpdateCertiCardPassRecord(String guid, String cardNo, String ELCode) {
        Connection conUCCPR2 = DBCDPSConnection();
        byte[] fileBytes = null;
        try {
            String RF = guid.toUpperCase();
            CallableStatement cstmtUCCPR2 = conUCCPR2.prepareCall("{ call dbo.SP_UpdateCertiCardPassRecord(?,?,?,?,?,?)}");
            cstmtUCCPR2.setString("DeviceID", xmlHelper.ReadValue("MachineID"));
            cstmtUCCPR2.setString("DirectionType", inRBtn.isChecked() ? "I" : "O");
            cstmtUCCPR2.setString("SensorCode", RF);
            cstmtUCCPR2.setString("CardNo", cardNo);
            cstmtUCCPR2.setString("EL_Code", ELCode);
            cstmtUCCPR2.registerOutParameter("ReturnMsg", java.sql.Types.VARCHAR);

            ResultSet rsUCCPR2 = cstmtUCCPR2.executeQuery();

            while (rsUCCPR2.next()) {
                fileBytes = rsUCCPR2.getBytes("Photofile");
            }

            if (fileBytes != null) {
                bitmap = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.length);
            }

            String ReturnMsg = "";
            if (!cstmtUCCPR2.getMoreResults()) {//此行判断是否还有更多的结果集,如果没有,接下来会处理output返回参数了
                ReturnMsg = cstmtUCCPR2.getString(6).trim();
            }
            if (ReturnMsg != null) {
                reMsg = ReturnMsg;
                return reMsg.contains("成功");
            } else {
                reMsg = "驗票失敗\r\n無法取得回傳訊息";
                return false;
            }
        } catch (Exception ex) {
            reMsg = "驗票失敗\r\n發生無法預期錯誤";
            return false;
        } finally {
            try {
                conUCCPR2.close();
            } catch (Exception ex) {

            }
        }
    }

    //中央下載資料SP
    public ResultSet DownloadDeviceSetup() {
        ResultSet rsDDS = null;
        Connection conDDS = DBCDPSConnection();
        try {
            CallableStatement cstmtDDS = conDDS.prepareCall("{ call dbo.SP_DownloadDeviceSetup(?)}");
            cstmtDDS.setString("DeviceID", xmlHelper.ReadValue("MachineID"));
            rsDDS = cstmtDDS.executeQuery();
        } catch (Exception e) {
            WriteLog.appendLog("OnlineTickets.java/DownloadDeviceSetup/Exception:" + e.toString());
        }
        return rsDDS;
    }

    //中央下載資料SP
    private String DownloadDeviceType() {
        Connection conDDT = DBCDPSConnection();
        String value = "1";
        try {
            CallableStatement cstmtDDT = conDDT.prepareCall("{ call dbo.SP_DownloadExhibitionSetup(?)}");
            cstmtDDT.setString("DEVICE_ID", xmlHelper.ReadValue("MachineID"));
            ResultSet rsDDT = cstmtDDT.executeQuery();
            while (rsDDT.next()) {
                if (rsDDT.getString("ES_ParamCode").equals("UDSTime")) {
                    UDSTime = Integer.parseInt(rsDDT.getString("ES_ParamValue"));
                }

                if (rsDDT.getString("ES_ParamCode").equals("IDCF")) {
                    value = rsDDT.getString("ES_ParamValue");
                }
            }
            return value;
        } catch (Exception e) {
            WriteLog.appendLog("OnlineTicket.java/DownloadDeviceType/Exception:" + e.toString());
            return "";
        } finally {
            try {
                conDDT.close();
            } catch (Exception e) {
                WriteLog.appendLog("OnlineTicket.java/conDDT.close()/Exception:" + e.toString());
                return "";
            }
        }
    }

    //取得展覽名稱、IP、帳號、密碼SP
    private ResultSet DownloadAllExhibition(String EL) {
        ResultSet rsDAE = null;
        Connection conDAE = DBCDPSConnection();
        try {
            //CallableStatement cstmt = conDownloadAllExhibition.prepareCall("{ call dbo.SP_DownloadExhibitionInfo(?)}");
            //cstmt.setString("EL_Code",EL);
            String query = "select E.EL_Code,convert(nvarchar,EL_Name) as EL_Name,EL_NameEng, EL_ShowType,EL_InitStartDate,EL_InitUntilDate,EL_ExhibitStartDate,EL_ExhibitUntilDate,EL_FinishStartDate,EL_FinishUntilDate,EL_Organizers,EL_Status,EL_DB_IP,EL_DB_Account,EL_DB_PWD " +
                    "from cExhibitionList E " +
                    "where E.EL_Code = '" + EL + "'";
            Statement stmtDAE = conDAE.createStatement();
            rsDAE = stmtDAE.executeQuery(query);
        } catch (Exception e) {
            WriteLog.appendLog("OnlineTicket.java/DownloadAllExhibition/Exception:" + e.toString());
        }
        return rsDAE;
    }

    //下載票劵型態別SP
    private ResultSet DownloadBadgeType() {
        ResultSet rsDBT = null;
        Connection conDBT = DBExhibitConnection();
        try {
            //CallableStatement cstmt = conDownloadAllExhibition.prepareCall("{ call dbo.SP_DownloadBadgeType(?)}");
            //cstmt.setString("DEVICE_ID",xmlHelper.ReadValue("MachineID"));
            String query = "select BT_TypeID,convert(nvarchar,BT_TypeName)as BT_TypeName,BT_MaterialType,convert(nvarchar,BT_Memo) as BT_Memo from eBadgeType";
            Statement stmtDBT = conDBT.createStatement();
            rsDBT = stmtDBT.executeQuery(query);
        } catch (Exception e) {
            WriteLog.appendLog("OnlineTicket.java/DownloadBadgeType/Exception:" + e.toString());
        }
        return rsDBT;
    }

    //取得AES256的密碼SP
    private String[] DownloadExhibitionSetup() {
        Connection conDES = DBExhibitConnection();
        ResultSet rsDES = null;
        String[] strQRPWD = {"", ""};
        try {
            CallableStatement cstmtDES = conDES.prepareCall("{ call dbo.SP_DownloadExhibitionSetup(?)}");
            cstmtDES.setString("Device_ID", xmlHelper.ReadValue("MachineID"));
            rsDES = cstmtDES.executeQuery();

            while (rsDES.next()) {
                if (rsDES.getString("ES_ParamCode").equals("QRPWD")) {
                    strQRPWD[0] = rsDES.getString("ES_ParamValue");
                    strQRPWD[1] = rsDES.getString("ES_ParamValue2");
                    return strQRPWD;
                }
            }
            return null;
        } catch (Exception e) {
            WriteLog.appendLog("OnlineTicket.java/DownloadExhibitionSetup/Exception:" + e.toString());
            return null;
        }
    }

    /// 主動回覆當前狀態SP
    private Boolean UpdateDeviceStatus() {
        Connection conUDS = DBExhibitConnection();
        String reportMessage = "";
        try {
            CallableStatement cstmtUDS = conUDS.prepareCall("{ call dbo.SP_UpdateDeviceStatus(?,?,?)}");
            cstmtUDS.setString("DeviceID", xmlHelper.ReadValue("MachineID"));
            cstmtUDS.setString("ServiceType", "0001");
            cstmtUDS.registerOutParameter("ReturnMsg", java.sql.Types.VARCHAR);
            cstmtUDS.execute();

            if (cstmtUDS.getString(3) != null) {
                reportMessage = cstmtUDS.getString(3).trim();
                return reportMessage.contains("成功");
            } else {
                return false;
            }
        } catch (Exception e) {
            WriteLog.appendLog("OnlineTicket.java/UpdateDeviceStatus/Exception:" + e.toString());
            return false;
        }
    }

    //設備回報
    private Boolean CheckOnline() {
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

    //定時主動回報狀態
    private Runnable updateTimer = new Runnable() {
        public void run() {
            UpdateDeviceStatus();
            handler.postDelayed(this, mainTimerInterval);
        }
    };

    // 解碼作業
    private String QRDecod(String _PacketData, String iv) {
        try {
            String newKey = "", strDecod = _PacketData;

            for (String strKey : sQRPWDItem) {
                newKey = MakeKeyLen32(strKey);
                byte[] descryptBytes = DecryptAES256(newKey.getBytes("UTF-8"), iv.getBytes("UTF-8"), Base64.decode(_PacketData, Base64.DEFAULT));
                if(descryptBytes!=null){
                    String descryptResult = new String(descryptBytes);
                    if(descryptResult.contains("@")){
                        if (descryptResult.split("@", -1).length == 17) {
                            strDecod = descryptResult;
                            break;
                        }
                    }
                }
            }
            return strDecod;
        } catch (Exception e) {
            WriteLog.appendLog("OnlineTicket.java/QRDecod/Exception:" + e.toString());
            return _PacketData;
        }
    }

    //QRCODE解碼
    public static byte[] DecryptAES256(byte[] keyBytes, byte[] ivBytes, byte[] valueBytes) {
        try {
            AlgorithmParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            SecretKeySpec newKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, newKey, ivSpec);
            return cipher.doFinal(valueBytes);
        } catch (Exception e) {
            WriteLog.appendLog("OnlineTicket.java/DecryptAES256/Exception:" + e.toString());
            return null;
        }
    }

    public static String byte2hex(byte[] b) { // 二进制转字符串
        String hs = "";
        String stmp = "";
        for (int n = 0; n < b.length; n++) {
            if (b[n] == (byte) 0) {
                continue;
            }
            stmp = (java.lang.Integer.toHexString(b[n] & 0XFF));
            if (stmp.length() == 1)
                hs = hs + "0" + stmp;
            else
                hs = hs + stmp;
        }
        return hs;
    }

    public String getHexToString(String strValue) {
        int intCounts = strValue.length() / 2;
        String strReturn = "";
        String strHex = "";
        int intHex = 0;
        byte byteData[] = new byte[intCounts];
        try {
            for (int intI = 0; intI < intCounts; intI++) {
                strHex = strValue.substring(0, 2);
                strValue = strValue.substring(2);
                intHex = Integer.parseInt(strHex, 16);
                if (intHex > 128)
                    intHex = intHex - 256;
                byteData[intI] = (byte) intHex;
            }
            strReturn = new String(byteData, "ISO8859-1");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return strReturn;
    }

    //Key長度補為32
    private String MakeKeyLen32(String sKey) {
        String Result = sKey;
        if (sKey.length() < 32) {
            Result = Result + String.format("%1$0" + (32 - sKey.length()) + "d", 0);
        }
        return Result;
    }

    //設定票券狀態文字
    private void setResultText(String text) {
        resultTxt.setText(text);
    }

    //設定票券狀態文字
    private void setResultText2(String text) {
        resultTxt2.setText(text);
    }

    //設定票券狀態文字
    private void setResultText3(String text) {
        resultTxt3.setText(text);
    }

    //震動
    public void setVibrate(int time) {
        Vibrator myVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        myVibrator.vibrate(time);
    }

    //取得現在時間 yyyy-MM-dd HH:mm:ss
    public String getDateTime() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar c = Calendar.getInstance();
        String str = df.format(c.getTime());
        return str;
    }

    //檢查網路是否連線
    public boolean checkInternetConnect() {
        ConnectivityManager cManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return cManager.getActiveNetworkInfo() != null;
    }

    //檢查與原先wifi是否一樣
    private boolean checkWifiChanged() {
        boolean changed = false;
        WifiInfo wifi = wifiManager.getConnectionInfo();
        if (wifi != null) {
            String bssid = wifi.getBSSID();
            changed = !previousBssid.equals(bssid);
        }
        return changed;
    }

    //監控wifi是否變更
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
                SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                if (SupplicantState.isValidState(state) && state == SupplicantState.COMPLETED) {
                    boolean changed = checkWifiChanged();
                    if (changed) {
                        Toast.makeText(OnlineTickets.this, "wifi change.", Toast.LENGTH_SHORT).show();
                        OnlineTickets.this.finish();
                    }
                }
            }
        }
    };

    //監控網路狀態
    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = connectMgr.getActiveNetworkInfo();
            if (mNetworkInfo == null) {
                Toast.makeText(OnlineTickets.this, "連線中斷", Toast.LENGTH_SHORT).show();
                OnlineTickets.this.finish();
            }
        }
    };
}