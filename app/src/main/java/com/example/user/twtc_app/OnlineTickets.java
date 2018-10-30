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
import android.graphics.Color;
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
    private String DEVICE_ID, strUserTicketName, reMsg, previousBssid, DL_Verify;
    private TextView stateTxt, allowTxt, nameTxt, datetimeTxt, inoutTxt, failedResultTxt, photoTxt;
    private ImageView resultImage, photoImage;
    private Button returnBtn, homeBtn;
    private RadioButton inRBtn, outRBtn;
    private LinearLayout wifiLayout, rfidLayout, succeedLayout;
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
    boolean reading = false;

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
        DEVICE_ID = intent.getStringExtra("DEVICE_ID");

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

        resultImage = (ImageView) findViewById(R.id.resultImage);
        photoImage = (ImageView) findViewById(R.id.photoImage);
        inoutTxt = (TextView) findViewById(R.id.inoutTxt);
        stateTxt = (TextView) findViewById(R.id.stateTxt);
        allowTxt = (TextView) findViewById(R.id.allowTxt);
        nameTxt = (TextView) findViewById(R.id.nameTxt);
        datetimeTxt = (TextView) findViewById(R.id.datetimeTxt);
        failedResultTxt = (TextView) findViewById(R.id.failedResultTxt);
        photoTxt = (TextView) findViewById(R.id.photoTxt);
        returnBtn = (Button) findViewById(R.id.returnBtn);
        homeBtn = (Button) findViewById(R.id.homeBtn);
        inRBtn = (RadioButton) findViewById(R.id.inRBtn);
        outRBtn = (RadioButton) findViewById(R.id.outRBtn);
        wifiLayout = (LinearLayout) findViewById(R.id.wifiLayout);
        rfidLayout = (LinearLayout) findViewById(R.id.rfidLayout);
        succeedLayout = (LinearLayout) findViewById(R.id.succeedLayout);
        cbMgr = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        wifiLayout.setVisibility(View.GONE);
        rfidLayout.setVisibility(View.GONE);


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
                if (worktype != null && !worktype.trim().equals("")) {
                    xmlHelper.WriteValue("WorkType", worktype);
                }

                //取得服務證驗證狀態
                String INCF_Type = DownloadDeviceType();
                if (!INCF_Type.trim().equals("")) {
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

        DL_Verify = DownloadDeviceSetupFromVM();
        if (DL_Verify == null || DL_Verify.equals("")) {
            DL_Verify = xmlHelper.ReadValue("InOutType");
        }

        xmlHelper.WriteValue("InOutType", DL_Verify);

        if (DL_Verify.equals("N")) {
            inRBtn.setEnabled(true);
            outRBtn.setEnabled(true);
            inRBtn.setChecked(true);
            inRBtn.setTextColor(Color.parseColor("#000000"));
            outRBtn.setTextColor(Color.parseColor("#000000"));
        } else if (DL_Verify.equals("I")) {
            inRBtn.setEnabled(true);
            outRBtn.setEnabled(false);
            inRBtn.setChecked(true);
            inRBtn.setTextColor(Color.parseColor("#000000"));
            outRBtn.setTextColor(Color.parseColor("#DDDDDD"));
        } else if (DL_Verify.equals("O")) {
            inRBtn.setEnabled(false);
            outRBtn.setEnabled(true);
            outRBtn.setChecked(true);
            inRBtn.setTextColor(Color.parseColor("#DDDDDD"));
            outRBtn.setTextColor(Color.parseColor("#000000"));
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
                    setVibrate(100);
                    wifiLayout.setVisibility(View.VISIBLE);
                    rfidLayout.setVisibility(View.GONE);
                    if (ary != null) ary = new String[ary.length];
                    //if (xmlHelper.ReadValue("WorkType").equals("W")) {//服務證驗證狀態只能用RFID驗票
                    if (1 == 2) {
                        setFailedResultText("票券錯誤\n無效票卡");
                        return;
                    }

                    String qr = cbMgr.getPrimaryClip().getItemAt(0).getText().toString();

                    //判斷是否為1D條碼
                    if (qr.length() <= 32 && qr.substring(0, 4).toUpperCase().equals("TWTC") || qr.length() == 16) {
                        BarCodeCheck(qr);
                        return;
                    }

                    String value = qr.substring(0, qr.length() - 16);
                    String iv = qr.substring(qr.length() - 16);
                    ary = QRDecod(value, iv).split("@", -1);

                    if (ary.length == 17) {
                        //檢查UUID 8-4-4-4-12
                        String[] sUUID = ary[0].toString().split("-", -1);
                        if (sUUID.length == 5) {
                            if (sUUID[0].length() != 8 || sUUID[1].length() != 4 || sUUID[2].length() != 4 || sUUID[3].length() != 4 || sUUID[4].length() != 12) {
                                setFailedResultText("票劵錯誤\n無效票卡!");
                                return;
                            }
                        }
                        tickCheck();
                    } else {
                        setFailedResultText("票劵錯誤\n無效票卡!");
                    }
                } catch (Exception ex) {
                    setFailedResultText("票劵錯誤\n無效票卡!");
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
                WiFiConnectSetting.activity.finish();
                OnlineTickets.this.finish();
            }
        });

        //RFID
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

        //註冊網路狀態監聽
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);

        //註冊wifi變換監聽
        IntentFilter intentFilter2 = new IntentFilter();
        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        registerReceiver(wifiChangeReceiver, intentFilter2);
    }//End ON CREATE

    @Override
    protected void onResume() {
        super.onResume();
        if (xmlHelper.ReadValue("RFID").equals("OPEN")) {
            mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
        }
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
        cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (xmlHelper.ReadValue("RFID").equals("OPEN")) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(connectionReceiver);
        unregisterReceiver(wifiChangeReceiver);
    }

    //RFID感應事件
    @Override
    protected void onNewIntent(Intent intent) {
        getTagInfo(intent);
    }

    //RFID驗票
    private void getTagInfo(Intent intent) {
        if (reading) {
            return;
        }
        reading = true;

        wifiLayout.setVisibility(View.GONE);
        rfidLayout.setVisibility(View.GONE);

        //清空圖像資訊
        bitmap = null;
        photoImage.setImageBitmap(bitmap);

        String RFData = "";
        String tagNo = "";
        String strCardID = "", strStartDate = "", strEndDate = "", strCardName = "";
        int iBlockCount = 0;
        byte[] pData;
        byte[][] pDatas;

        try {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            MifareClassic mfc = MifareClassic.get(tag);
            mfc.connect();

            if (mfc.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT)) {
                BlockInfo.bCardType = mfc.readBlock(1)[0];
                if (BlockInfo.bCardType >= 0x01 && BlockInfo.bCardType <= 0x0F) {
                    iBlockCount = BlockInfo.bCardType; //讀取會用到的Block數，從第Block4開始起算
                    pDatas = new byte[iBlockCount + 4 + (iBlockCount + 4) / 4][16];
                    for (int i = 1; i <= (iBlockCount + 4 + (iBlockCount + 4) / 4) / 4; i++) {
                        if (mfc.authenticateSectorWithKeyA(i, MifareClassic.KEY_DEFAULT)) {
                            for (int j = 0; j < 4; j++) {
                                int block = i * 4 + j;
                                if (block == 0 || block == 7 || block == 11 || block == 15 || block == 19 || block > pDatas.length - 1) {
                                    continue;
                                }
                                pDatas[block] = mfc.readBlock(block);
                            }
                        } else {
                            setFailedResultText("票券錯誤\n票卡讀取失敗！");
                            reading = false;
                            return;
                        }
                    }

                    for (int iBlock = 4; iBlock < iBlockCount + 4; iBlock++) {
                        //避開 security block
                        if (iBlock == 7 || iBlock == 11 || iBlock == 15 || iBlock == 19) {
                            iBlockCount++;
                            continue;
                        }

                        pData = pDatas[iBlock];
                        RFData = RFData + getHexToString(byte2hex(pData));
                    }
                } else if (BlockInfo.bCardType == (byte) 0xFC || BlockInfo.bCardType == (byte) 0xFD) {
                    iBlockCount = 1; //僅讀取Block4
                    pDatas = new byte[iBlockCount + 4][16];
                    if (mfc.authenticateSectorWithKeyA(1, MifareClassic.KEY_DEFAULT)) {
                        pDatas[4] = mfc.readBlock(4);
                        pData = pDatas[4];
                        try {
                            System.arraycopy(pData, 0, BlockInfo.bStartDate, 0, 3);
                            System.arraycopy(pData, 3, BlockInfo.bEndDate, 0, 3);
                            System.arraycopy(pData, 6, BlockInfo.bCardID, 0, 8);
                        } catch (Exception e) {
                            WriteLog.appendLog("OnlineTickets.java/DownloadDeviceSetup/Exception:" + e.toString());
                            reading = false;
                            return;
                        }
                    } else {
                        setFailedResultText("票券錯誤\n票卡讀取失敗！");
                        reading = false;
                        return;
                    }
                } else { //其他證別(Mifare)
                    if (UpdateImportPass(tagNo, false, "") == true) {
                        setSucceedResultText(reMsg, strUserTicketName);
                    } else {
                        setFailedResultText("票券錯誤\n" + reMsg);
                    }
                    reading = false;
                    return;
                }
            } else {
                setFailedResultText("票券錯誤\n票卡讀取失敗！");
                reading = false;
                return;
            }

            byte[] tagId = tag.getId();
            for (int i = 0; i < tagId.length; i++) {
                if (Integer.toHexString(tagId[i] & 0xFF).length() < 2) {
                    tagNo += "0";
                }
                tagNo += Integer.toHexString(tagId[i] & 0xFF).toUpperCase();
            }

            ary = RFData.split("@", -1);

            if (BlockInfo.bCardType == (byte) 0xFC || BlockInfo.bCardType == (byte) 0xFD) {
                //判斷當前是否允許服務證驗證
                //if (!xmlHelper.ReadValue("WorkType").equals("W")) {
                if (!(1 == 1)) {
                    setFailedResultText("票券錯誤\n不允許服務證驗證");
                } else {
                    if (BlockInfo.bCardType == (byte) 0xFD) { //服務證
                        if (BlockInfo.bCardID[0] == 'A' || BlockInfo.bCardID[0] == 'G' || BlockInfo.bCardID[0] == 'N') {
                            strCardName = "服務證";
                        } else {
                            setFailedResultText("票券錯誤\n(格式)無效服務證");
                        }
                    }
                    if (BlockInfo.bCardType == (byte) 0xFC) { //臨時證
                        if ((BlockInfo.bCardID[0] == 'A' || BlockInfo.bCardID[0] == 'G' || BlockInfo.bCardID[0] == 'N') &&
                                (BlockInfo.bCardID[1] == 'D' || BlockInfo.bCardID[1] == 'D' || BlockInfo.bCardID[1] == 'D')) {
                            strCardName = "臨時證";
                        } else {
                            setFailedResultText("票券錯誤\n(格式)無效臨時證");
                        }
                    }
                    try {
                        //轉換ASCII to ANSI
                        strCardID = new String(BlockInfo.bCardID, "UTF-8");
                        strStartDate = String.format("%04d", 2000 + (BlockInfo.bStartDate[0] & 0xFF)) + String.format("%02d", BlockInfo.bStartDate[1] & 0xFF) + String.format("%02d", BlockInfo.bStartDate[2] & 0xFF);
                        strEndDate = String.format("%04d", 2000 + (BlockInfo.bEndDate[0] & 0xFF)) + String.format("%02d", BlockInfo.bEndDate[1] & 0xFF) + String.format("%02d", BlockInfo.bEndDate[2] & 0xFF);
                        Date date = new Date();
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                        String strNowDate = sdf.format(date);
                        //判斷當前時間與有效時間
                        if (Integer.parseInt(strNowDate) < Integer.parseInt(strStartDate) || Integer.parseInt(strNowDate) > Integer.parseInt(strEndDate)) {
                            setFailedResultText("票劵錯誤\n(逾期)無效票卡");
                        } else { //向中央取得權限
                            if (UpdateCertiCardPassRecord(tagNo, strCardID, xmlHelper.ReadValue("NameCode")) == true) {
                                //顯示登入者圖像
                                if (bitmap != null) {
                                    rfidLayout.setVisibility(View.VISIBLE);
                                    photoImage.setImageBitmap(bitmap);
                                }
                                photoTxt.setText("票券狀態    驗票成功\n票券身分    " + strCardName + (inRBtn.isChecked() ? "\n票券入場紀錄\n" : "\n票券出場紀錄\n") + getDateTime());
                            } else {
                                setFailedResultText("票券錯誤\n" + reMsg);
                            }
                        }
                    } catch (Exception ex) {
                        setFailedResultText("票劵錯誤\n(異常)請重新確認");
                        reading = false;
                    }
                }
            } else {
                if (ary.length == 17) {
                    if (UpdatePassRecord(tagNo, false, ary) == true) {
                        setSucceedResultText(reMsg, mydbHelper.GetBadgeType(ary[2].toString()));
                    } else {
                        setFailedResultText("票劵錯誤\n" + reMsg);
                    }
                } else {
                    setFailedResultText("票劵錯誤\n無效票卡");
                }
            }
            reading = false;
        } catch (IOException ex) {
            reading = false;
            WriteLog.appendLog("OnlineTicket.java/DownloadDeviceType/Exception:" + ex.toString());
        }
    }

    //一維條碼(BARCODE)驗票
    private void BarCodeCheck(String qr) {
        if (UpdateImportPass("", true, qr) == true) {
            setSucceedResultText(reMsg, strUserTicketName);
        } else {
            setFailedResultText("票劵錯誤\n" + reMsg);
        }
    }

    //二維條碼(QRCODE)驗票
    private void tickCheck() {
        if (UpdatePassRecord(ary[0].toString().trim(), true, ary) == true) {
            setSucceedResultText(reMsg, mydbHelper.GetBadgeType(ary[2].toString()));
        } else {
            setFailedResultText("票劵錯誤\n" + reMsg);
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
                    String[] guidary = guid.split("-", -1);
                    for (String guidstr : guidary) {
                        RF += guidstr;
                    }
                }
            }
            CallableStatement cstmtUIP = conUIP.prepareCall("{ call dbo.SP_UpdateImportPass(?)}");
            cstmtUIP.setString("DeviceID", DEVICE_ID);
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
    private Boolean UpdatePassRecord(String guid, boolean type, String[] QRarray) {
        Connection conUPR = DBExhibitConnection();
        try {
            //UUID u = UUID.nameUUIDFromBytes(QRarray[0].getBytes());
            UUID u = UUID.fromString(QRarray[0]);
            String RF = "";

            if (type == false) {//為true時候代表刷讀票劵，false為RFID
                RF = guid;
            }

            CallableStatement cstmtUPR = conUPR.prepareCall("{ call dbo.SP_UpdatePassRecord(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
            cstmtUPR.setString("DeviceID", DEVICE_ID);
            cstmtUPR.setString("DirectionType", inRBtn.isChecked() ? "I" : "O");
            cstmtUPR.setString("SensorCode", RF);
            cstmtUPR.setString("SysCode", u.toString());
            cstmtUPR.setString("EL_Code", QRarray[1].toString().trim());
            //cstmtUPR.setString("EL_Code", "TE2017");
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
            cstmtUPR.setString("VP_ValidTimeBegin", QRarray[7].toString().trim());
            //cstmtUPR.setString("VP_ValidTimeBegin", "20180101");
            cstmtUPR.setString("VP_ValidTimeEnd", QRarray[8].toString().trim());
            //cstmtUPR.setString("VP_ValidTimeEnd", "20181231");
            cstmtUPR.setString("VP_UseAreaAssign", QRarray[9].toString().trim());
            cstmtUPR.setString("VP_UsageTimeType",QRarray[10].toString().trim());
            //cstmtUPR.setString("VP_UsageTimeType", "0");
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
            cstmtUCCPR2.setString("DeviceID", DEVICE_ID);
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
            cstmtDDS.setString("DeviceID", DEVICE_ID);
            rsDDS = cstmtDDS.executeQuery();
        } catch (Exception e) {
            WriteLog.appendLog("OnlineTickets.java/DownloadDeviceSetup/Exception:" + e.toString());
        }
        return rsDDS;
    }

    //VM下載資料SP
    public String DownloadDeviceSetupFromVM() {
        String DL_Verify = "";
        Connection conDDSFV = DBExhibitConnection();
        try {
            CallableStatement cstmtDDSFV = conDDSFV.prepareCall("{ call dbo.SP_DownloadDeviceSetupFromVM(?)}");
            cstmtDDSFV.setString("DEVICE_ID", DEVICE_ID);
            ResultSet rsDDSFV = cstmtDDSFV.executeQuery();
            while (rsDDSFV.next()) {
                DL_Verify = rsDDSFV.getString("DL_Verify");
            }
        } catch (Exception e) {
            WriteLog.appendLog("OnlineTickets.java/DownloadDeviceSetupFromVM/Exception:" + e.toString());
        }
        return DL_Verify;
    }

    //中央下載資料SP
    private String DownloadDeviceType() {
        Connection conDDT = DBCDPSConnection();
        String value = "1";
        try {
            CallableStatement cstmtDDT = conDDT.prepareCall("{ call dbo.SP_DownloadExhibitionSetup(?)}");
            cstmtDDT.setString("DEVICE_ID", DEVICE_ID);
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
            //cstmt.setString("DEVICE_ID",DEVICE_ID);
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
            cstmtDES.setString("Device_ID", DEVICE_ID);
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
            cstmtUDS.setString("DeviceID", DEVICE_ID);
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
            cstmt.setString("DeviceID", DEVICE_ID);
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
                if (descryptBytes != null) {
                    String descryptResult = new String(descryptBytes);
                    if (descryptResult.contains("@")) {
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

    //票券狀態文字
    private void setSucceedResultText(String state, String name) {
        wifiLayout.setVisibility(View.VISIBLE);
        inoutTxt.setText((inRBtn.isChecked() ? "票券入場紀錄" : "票券出場紀錄"));
        stateTxt.setText(state);
        allowTxt.setText("參觀證");
        nameTxt.setText(name);
        datetimeTxt.setText(getDateTime());
        succeedLayout.setVisibility(View.VISIBLE);
        failedResultTxt.setVisibility(View.GONE);
        resultImage.setImageResource(R.drawable.ticket_success);
    }

    //票券狀態文字
    private void setFailedResultText(String text) {
        wifiLayout.setVisibility(View.VISIBLE);
        succeedLayout.setVisibility(View.GONE);
        failedResultTxt.setVisibility(View.VISIBLE);
        failedResultTxt.setText(text);
        resultImage.setImageResource(R.drawable.ticket_failed);
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
    private final BroadcastReceiver wifiChangeReceiver = new BroadcastReceiver() {
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