package com.example.user.twtc_app;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import net.sourceforge.jtds.jdbc.DateTime;

import java.io.IOException;
import java.security.spec.AlgorithmParameterSpec;
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
public class OfflineTickets extends Activity {
    TextView ResultTxt, ResultTxt2;
    RadioButton InRBtn;
    Button ReturnBtn;
    LinearLayout FailedLayout;
    String result, DeviceID, strSQL, EL;
    String[] ary;
    String[] sQRPWDItem = new String[2];

    //剪貼簿
    ClipboardManager cbMgr;
    ClipboardManager.OnPrimaryClipChangedListener mPrimaryClipChangedListener;

    //SQLITE
    MyDBHelper mydbHelper;
    XmlHelper xmlHelper;

    //RFID
    NfcAdapter mNfcAdapter;
    PendingIntent mPendingIntent;

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
    RFIDBlock4Info BlockInfo = new RFIDBlock4Info(new byte[3], new byte[3], new byte[8]);

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.offline_tickets);

        mydbHelper = new MyDBHelper(this);
        xmlHelper = new XmlHelper(getFilesDir() + "//connectData.xml");

        //判斷是否當前為服務證以及關閉驗證狀態
        /*if (xmlHelper.ReadValue("WorkType").toUpperCase().equals("W") && xmlHelper.ReadValue("IDCF").toUpperCase().equals("0")) {
            Toast.makeText(OfflineTickets.this, "當前作業狀態不允許服務證驗證", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }*/

        ReturnBtn = (Button) findViewById(R.id.ReturnBtn);
        ResultTxt = (TextView) findViewById(R.id.ResultTxt);
        ResultTxt2 = (TextView) findViewById(R.id.ResultTxt2);
        FailedLayout = (LinearLayout) findViewById(R.id.FailedLayout);
        InRBtn = (RadioButton) findViewById(R.id.InRBtn);
        cbMgr = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        InRBtn.setChecked(true);

        sQRPWDItem[0] = xmlHelper.ReadValue("Key1");
        sQRPWDItem[1] = xmlHelper.ReadValue("Key2");
        EL = xmlHelper.ReadValue("NameCode");
        DeviceID = xmlHelper.ReadValue("MachineID");

        //region 掃描事件
        mPrimaryClipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
            public void onPrimaryClipChanged() {
                try {
                    setVibrate(100);
                    String qr = cbMgr.getPrimaryClip().getItemAt(0).getText().toString();
                    String value = qr.substring(0, qr.length() - 16);
                    String iv = qr.substring(qr.length() - 16);
                    Calendar c = Calendar.getInstance();
                    SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
                    SimpleDateFormat df2 = new SimpleDateFormat("HHmm");

                    //QRCode 狀態下不驗證服務證與工作證
                    /*if (xmlHelper.ReadValue("WorkType").toUpperCase().equals("W")) {
                        FailedLayout.setVisibility(View.VISIBLE);
                        setResultText(result = "票券狀態    ");
                        setResultText2(result = "票劵錯誤，無效票卡");
                        return;
                    }*/

                    if (qr.length() < 30) {
                        FailedLayout.setVisibility(View.VISIBLE);
                        setResultText(result = "票券狀態    ");
                        setResultText2(result = "票劵錯誤\r\n(格式)無效票卡");
                        return;
                    }

                    ary = QRDecod(value, iv).split("@");
                    String a = ary[5];
                    if (ary.length == 17) {
                        //檢查UUID 8-4-4-4-12
                        String[] sUUID = ary[0].split("-");
                        if (sUUID.length == 5) {
                            if (sUUID[0].length() != 8 || sUUID[1].length() != 4 || sUUID[2].length() != 4 || sUUID[3].length() != 4 || sUUID[4].length() != 12) {
                                FailedLayout.setVisibility(View.VISIBLE);
                                setResultText(result = "票券狀態    ");
                                setResultText2(result = "票劵錯誤\r\n(格式)無效票卡");
                                return;
                            }
                        }

                        if (!ary[1].trim().contains(EL)) {
                            FailedLayout.setVisibility(View.VISIBLE);
                            setResultText(result = "票券狀態    ");
                            setResultText2(result = "無效票卡\n展覽代號不符合");
                            savedate(ary[0].trim(), true, ary, "C");
                        } else {
                            //離線狀態下出場規則
                            if ((InRBtn.isChecked() ? "I" : "O").equals("O")) {
                                FailedLayout.setVisibility(View.GONE);
                                savedate(ary[0].trim(), true, ary, "A");
                                tickCheck();
                                return;
                            }

                            DateTime DTStart, DTEnd;
                            int iTotalINTime = 0, iTotalToDayINTime = 0;
                            //入場日期判斷
                            if (ary[3].trim().equals("A"))//不限制入場日期
                            {
                                //直接進入下一回合
                                FailedLayout.setVisibility(View.GONE);
                                savedate(ary[0].trim(), false, ary, "A");
                                tickCheck();
                            } else if (ary[3].trim().equals("B"))//起始日期與截止日期相同(與系統日期)
                            {
                                if (!ary[4].equals(ary[5])) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "票券日期規則不符");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                } else//相同時間後比較系統時間
                                {
                                    if (!ary[4].equals(df.format(c.getTime()))) {
                                        FailedLayout.setVisibility(View.VISIBLE);
                                        setResultText(result = "票券狀態    ");
                                        setResultText2(result = "已過入場日期");
                                        savedate(ary[0].trim(), true, ary, "C");
                                        return;
                                    }
                                }
                            } else if (ary[3].trim() == "C")//依據起始日期與截止日期判斷
                            {
                                int tsStartDay, tsEndDay;
                                tsStartDay = Integer.parseInt(df.format(c.getTime())) - Integer.parseInt(ary[4]);
                                tsEndDay = Integer.parseInt(df.format(c.getTime())) - Integer.parseInt(ary[5]);

                                if (tsStartDay < 0) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "未到入場日期");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                } else if (tsEndDay > 0) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "已過入場日期");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                }
                            } else {
                                FailedLayout.setVisibility(View.VISIBLE);
                                setResultText(result = "票券狀態    ");
                                setResultText2(result = "票劵錯誤\r\n(格式)無效票卡");
                                savedate(ary[0].trim(), true, ary, "C");
                                return;
                            }
                            //入場時段判斷
                            if (ary[6].trim().equals("A")) {
                                //直接再進入下一回合
                            } else if (ary[6].trim() == "B") {

                                if ((Integer.parseInt(ary[7].substring(0, 2)) > Integer.parseInt(df2.format(c.getTime()).substring(0, 2)))
                                        || (Integer.parseInt(ary[7].substring(0, 2)) == Integer.parseInt(df2.format(c.getTime()).substring(0, 2)))
                                        && ((Integer.parseInt(ary[7].substring(2)) > Integer.parseInt(df2.format(c.getTime()).substring(2))))) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "未到入場時間");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                } else if ((Integer.parseInt(ary[8].substring(0, 2)) < Integer.parseInt(df2.format(c.getTime()).substring(0, 2)))
                                        || (Integer.parseInt(ary[8].substring(0, 2)) == Integer.parseInt(df2.format(c.getTime()).substring(0, 2)))
                                        && ((Integer.parseInt(ary[8].substring(2)) < Integer.parseInt(df2.format(c.getTime()).substring(2))))) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "已過入場時間");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                }
                            } else {
                                FailedLayout.setVisibility(View.VISIBLE);
                                setResultText(result = "票券狀態    ");
                                setResultText2(result = "票劵錯誤\n(格式)無效票卡");
                                savedate(ary[0].trim(), true, ary, "C");
                                return;
                            }

                            if (ary[9].length() % 2 != 0) //入場展區必為偶數
                            {
                                FailedLayout.setVisibility(View.VISIBLE);
                                setResultText(result = "票券狀態    ");
                                setResultText2(result = "票劵錯誤\n(格式)無效票卡");
                                savedate(ary[0].trim(), true, ary, "C");
                                return;
                            }

                            //先取得相關資料
                            iTotalINTime = mydbHelper.GetLoginCount(ary[0].trim(), "");
                            iTotalToDayINTime = mydbHelper.GetLoginCount(ary[0].trim(), df.format(c.getTime()));

                            if (ary[10].trim() == "0")//入場次數不限制
                            {
                                //直接再進入下一回合
                            } else if (ary[10].trim().equals("1") || ary[10].trim().equals("3")) //限制總入場次數
                            {
                                if (Integer.parseInt(ary[11].trim()) < iTotalINTime) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "票劵已達使用上限");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                }
                            } else if (ary[10].trim().equals("2") || ary[10].trim().equals("3")) //限制當日入場次數
                            {
                                if (Integer.parseInt(ary[12].trim()) < iTotalToDayINTime) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "票劵已達使用上限");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                }
                            }

                            if (ary[14].trim() == "Y")//判斷是否已停用
                            {
                                FailedLayout.setVisibility(View.VISIBLE);
                                setResultText(result = "票券狀態    ");
                                setResultText2(result = "票劵已停用");
                                savedate(ary[0].trim(), true, ary, "C");
                                return;
                            }


                            //進場
                            FailedLayout.setVisibility(View.GONE);
                            savedate(ary[0].trim(), true, ary, "A");
                            tickCheck();
                        }
                    } else {
                        FailedLayout.setVisibility(View.VISIBLE);
                        setResultText(result = "票券狀態    ");
                        setResultText2(result = "票劵錯誤\r\n(格式)無效票卡");
                    }
                } catch (Exception ex) {
                    FailedLayout.setVisibility(View.VISIBLE);
                    setResultText(result = "票券狀態    ");
                    setResultText2(result = "票劵錯誤\r\n(異常)無效票卡");
                }
            }
        };
        //endregion
        cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);

        ReturnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        //RFID
        if (xmlHelper.ReadValue("RFID").equals("OPEN")) {
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (mNfcAdapter == null) {
                Toast.makeText(OfflineTickets.this, "此裝置無NFC功能，無法進行RFID驗票！", Toast.LENGTH_SHORT).show();
                return;
            } else {
                if (!mNfcAdapter.isEnabled()) {
                    Toast.makeText(OfflineTickets.this, "NFC功能尚未開啟，請至設定開啟！", Toast.LENGTH_SHORT).show();
                }
            }
            mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                    getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        }
    }//END ONCREATE

    @Override
    protected void onResume() {
        super.onResume();
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
        cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);
        mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
        mNfcAdapter.disableForegroundDispatch(this);
    }

    //RFID感應事件
    @Override
    protected void onNewIntent(Intent intent) {
        getTagInfo(intent);
    }

    //RFID驗票
    private void getTagInfo(Intent intent) {
        String RFData = "";
        String tagNo = "";
        String strCardID = "", strStartDate = "", strEndDate = "", strCardName = "";
        DateTime dtStartDate, dtEndDate;
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat df2 = new SimpleDateFormat("HHmm");
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
        MifareClassic mfc = MifareClassic.get(tag);

        try {
            mfc.connect();
            boolean auth = false;

            auth = mfc.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT);
            if (auth) {
                //讀取卡別資訊
                pData = mfc.readBlock(1);

                if (pData[0] == (byte) 0xFC || pData[0] == (byte) 0xFD) { //服務證與臨時證
                    BlockInfo.bCardType = pData[0];
                    iBlockCount = 1; //僅讀取Block4
                } else if (pData[0] >= 0x01 && pData[0] <= 0x0F) { //參觀證或其他
                    BlockInfo.bCardType = 0;
                    iBlockCount = pData[0];
                    if (iBlockCount > 15 || iBlockCount == 0)//區塊超出範圍
                    {
                        FailedLayout.setVisibility(View.VISIBLE);
                        setResultText(result = "票券狀態    ");
                        setResultText2(result = "票劵錯誤\r\n(格式)無效票卡");
                    }
                }

                for (int iBlock = 4; iBlock < iBlockCount + 4; iBlock++) {
                    //避開 security block
                    if (iBlock == 7 || iBlock == 11 || iBlock == 15 || iBlock == 19) {
                        iBlockCount++;
                        continue;
                    }
                    if (mfc.authenticateSectorWithKeyA(iBlock / 4, MifareClassic.KEY_DEFAULT)) {
                        pData = mfc.readBlock(iBlock);
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
                    }
                }

                ary = RFData.split("@");

                if (BlockInfo.bCardType == (byte) 0xFC || BlockInfo.bCardType == (byte) 0xFD) {
                    //判斷當前是否允許服務證驗證
                    //if (!xmlHelper.ReadValue("WorkType").equals("W")) {
                    if (!(1 == 1)) {
                        FailedLayout.setVisibility(View.VISIBLE);
                        setResultText(result = "票券狀態    ");
                        setResultText2(result = "驗證模式\r\n不允許服務證驗證");
                    } else {
                        if (BlockInfo.bCardType == (byte) 0xFD) { //服務證
                            if (BlockInfo.bCardID[0] == 'A' || BlockInfo.bCardID[0] == 'G' || BlockInfo.bCardID[0] == 'N') {
                                //do nothing
                                strCardName = "服務證";
                            } else {
                                FailedLayout.setVisibility(View.VISIBLE);
                                setResultText(result = "票券狀態    ");
                                setResultText2(result = "票劵錯誤\r\n(格式)無效服務證");
                            }
                        }
                        if (BlockInfo.bCardType == (byte) 0xFC) { //臨時證
                            if ((BlockInfo.bCardID[0] == 'A' || BlockInfo.bCardID[0] == 'G' || BlockInfo.bCardID[0] == 'N') &&
                                    (BlockInfo.bCardID[1] == 'D' || BlockInfo.bCardID[1] == 'D' || BlockInfo.bCardID[1] == 'D')) {
                                //do nothing
                                strCardName = "臨時證";
                            } else {
                                FailedLayout.setVisibility(View.VISIBLE);
                                setResultText(result = "票券狀態    ");
                                setResultText2(result = "票劵錯誤\r\n(格式)無效臨時證");
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
                            int iStartDay, iEndDay;
                            iStartDay = Integer.parseInt(strNowDate) - Integer.parseInt(strStartDate);
                            iEndDay = Integer.parseInt(strNowDate) - Integer.parseInt(strEndDate);
                            //判斷當前時間與有效時間
                            if ((iStartDay < 0 || iEndDay > 0) && (InRBtn.isChecked() ? "I" : "O").equals("I")) {
                                FailedLayout.setVisibility(View.VISIBLE);
                                setResultText(result = "票券狀態    ");
                                setResultText2(result = "票劵錯誤\r\n(逾期)無效票卡");
                                saveworkdate(tagNo, (InRBtn.isChecked() ? "I" : "O"), strCardID, xmlHelper.ReadValue("NameCode"), "C");
                            } else//證別有效時間正確即可
                            {
                                FailedLayout.setVisibility(View.GONE);
                                setResultText(result = "票券狀態    驗票成功\n\n票券身分    " + strCardName + "\n\n票券入場紀錄\n\n" + getDateTime());
                                RFData = "";
                                saveworkdate(tagNo, (InRBtn.isChecked() ? "I" : "O"), strCardID, xmlHelper.ReadValue("NameCode"), "A");
                            }
                        } catch (Exception ex) {
                            FailedLayout.setVisibility(View.VISIBLE);
                            setResultText(result = "票券狀態    ");
                            setResultText2(result = "票劵錯誤\r\n(異常)請重新確認");
                        }
                    }
                } else {
                    if (ary.length == 17) {
                        if (!ary[1].trim().contains(EL)) {
                            FailedLayout.setVisibility(View.VISIBLE);
                            setResultText(result = "票券狀態    ");
                            setResultText2(result = "無效票卡\n\n展覽代號不符合");
                            savedate(ary[0].trim(), true, ary, "C");
                        } else {
                            //離線狀態下出場規則
                            if ((InRBtn.isChecked() ? "I" : "O").equals("O")) {
                                FailedLayout.setVisibility(View.GONE);
                                savedate(ary[0].trim(), true, ary, "A");
                                tickCheck();
                                return;
                            }

                            DateTime DTStart, DTEnd;
                            int iTotalINTime = 0, iTotalToDayINTime = 0;
                            //入場日期判斷
                            if (ary[3].trim().equals("A"))//不限制入場日期
                            {
                                //直接進入下一回合
                                FailedLayout.setVisibility(View.GONE);
                                savedate(ary[0].trim(), false, ary, "A");
                                tickCheck();
                            } else if (ary[3].trim().equals("B"))//起始日期與截止日期相同(與系統日期)
                            {
                                if (!ary[4].equals(ary[5])) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "票券日期規則不符");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                } else//相同時間後比較系統時間
                                {
                                    if (!ary[4].equals(df.format(c.getTime()))) {
                                        FailedLayout.setVisibility(View.VISIBLE);
                                        setResultText(result = "票券狀態    ");
                                        setResultText2(result = "已過入場日期");
                                        savedate(ary[0].trim(), true, ary, "C");
                                        return;
                                    }
                                }
                            } else if (ary[3].trim() == "C")//依據起始日期與截止日期判斷
                            {
                                int tsStartDay, tsEndDay;
                                tsStartDay = Integer.parseInt(df.format(c.getTime())) - Integer.parseInt(ary[4]);
                                tsEndDay = Integer.parseInt(df.format(c.getTime())) - Integer.parseInt(ary[5]);

                                if (tsStartDay < 0) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "未到入場日期");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                } else if (tsEndDay > 0) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "已過入場日期");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                }
                            } else {
                                FailedLayout.setVisibility(View.VISIBLE);
                                setResultText(result = "票券狀態    ");
                                setResultText2(result = "票劵錯誤\r\n(格式)無效票卡");
                                savedate(ary[0].trim(), true, ary, "C");
                                return;
                            }
                            //入場時段判斷
                            if (ary[6].trim().equals("A")) {
                                //直接再進入下一回合
                            } else if (ary[6].trim() == "B") {

                                if ((Integer.parseInt(ary[7].substring(0, 2)) > Integer.parseInt(df2.format(c.getTime()).substring(0, 2)))
                                        || (Integer.parseInt(ary[7].substring(0, 2)) == Integer.parseInt(df2.format(c.getTime()).substring(0, 2)))
                                        && ((Integer.parseInt(ary[7].substring(2)) > Integer.parseInt(df2.format(c.getTime()).substring(2))))) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "未到入場時間");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                } else if ((Integer.parseInt(ary[8].substring(0, 2)) < Integer.parseInt(df2.format(c.getTime()).substring(0, 2)))
                                        || (Integer.parseInt(ary[8].substring(0, 2)) == Integer.parseInt(df2.format(c.getTime()).substring(0, 2)))
                                        && ((Integer.parseInt(ary[8].substring(2)) < Integer.parseInt(df2.format(c.getTime()).substring(2))))) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "已過入場時間");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                }
                            } else {
                                FailedLayout.setVisibility(View.VISIBLE);
                                setResultText(result = "票券狀態    ");
                                setResultText2(result = "票劵錯誤\n(格式)無效票卡");
                                savedate(ary[0].trim(), true, ary, "C");
                                return;
                            }

                            if (ary[9].length() % 2 != 0) //入場展區必為偶數
                            {
                                FailedLayout.setVisibility(View.VISIBLE);
                                setResultText(result = "票券狀態    ");
                                setResultText2(result = "票劵錯誤\n(格式)無效票卡");
                                savedate(ary[0].trim(), true, ary, "C");
                                return;
                            }

                            //先取得相關資料
                            iTotalINTime = mydbHelper.GetLoginCount(ary[0].trim(), "");
                            iTotalToDayINTime = mydbHelper.GetLoginCount(ary[0].trim(), df.format(c.getTime()));

                            if (ary[10].trim() == "0")//入場次數不限制
                            {
                                //直接再進入下一回合
                            } else if (ary[10].trim().equals("1") || ary[10].trim().equals("3")) //限制總入場次數
                            {
                                if (Integer.parseInt(ary[11].trim()) < iTotalINTime) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "票劵已達使用上限");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                }
                            } else if (ary[10].trim().equals("2") || ary[10].trim().equals("3")) //限制當日入場次數
                            {
                                if (Integer.parseInt(ary[12].trim()) < iTotalToDayINTime) {
                                    FailedLayout.setVisibility(View.VISIBLE);
                                    setResultText(result = "票券狀態    ");
                                    setResultText2(result = "票劵已達使用上限");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                }
                            }

                            if (ary[14].trim() == "Y")//判斷是否已停用
                            {
                                FailedLayout.setVisibility(View.VISIBLE);
                                setResultText(result = "票券狀態    ");
                                setResultText2(result = "票劵已停用");
                                savedate(ary[0].trim(), true, ary, "C");
                                return;
                            }


                            //進場
                            FailedLayout.setVisibility(View.GONE);
                            savedate(ary[0].trim(), true, ary, "A");
                            tickCheck();
                        }
                    } else {
                        setResultText(result = "票券狀態    ");
                        setResultText2(result = "票劵錯誤\r\n(格式)無效票卡");
                    }
                }
            } else { // Authentication failed - Handle it

            }
        } catch (IOException ex) {
            WriteLog.appendLog("OnlineTicket.java/DownloadDeviceType/Exception:" + ex.toString());
        }
    }

    private void savedate(String guid, Boolean type, String[] QRarray, String checkCode) {
        try {
            UUID u = UUID.nameUUIDFromBytes(QRarray[0].getBytes());
            String RF;
            String VP_ValidDateBegin;
            String VP_ValidDateEnd;

            if (type)   //為true時候代表刷讀票劵，false為RFID
            {
                RF = "";
            } else {
                RF = guid;
            }

            if (!QRarray[4].trim().equals("")) {
                VP_ValidDateBegin = QRarray[4].trim();
            } else {
                VP_ValidDateBegin = "";
            }
            if (!QRarray[5].trim().equals("")) {
                VP_ValidDateEnd = QRarray[5].trim();
            } else {
                VP_ValidDateEnd = "";
            }
            String tmp16 = QRarray[16].replaceAll("[0]+$", "");
            strSQL = "Insert Into BarcodeLog (DeviceID,DirectionType,SensorCode,SysCode,Current_EL_Code,EL_Code,BT_TypeID,VP_ValidDateRule,VP_ValidDateBegin,VP_ValidDateEnd,VP_ValidTimeRule" +
                    ",VP_ValidTimeBegin,VP_ValidTimeEnd,VP_UseAreaAssign,VP_UsageTimeType,VP_UsageTimeTotal,VP_UsageTimePerDay,IV_CheckCode,IV_CheckCode2,Result,SenseDT)" +
                    "Values('" + DeviceID + "','" + (InRBtn.isChecked() ? "I" : "O") + "','" + RF + "','" + u.toString() + "','" + QRarray[1].trim() + "','" + EL + "','" +
                    QRarray[2].trim() + "','" + QRarray[3].trim() + "','" + VP_ValidDateBegin + "','" + VP_ValidDateEnd + "','" +
                    QRarray[6].trim() + "','" + QRarray[7].trim() + "','" + QRarray[8].trim() + "','" + QRarray[9].trim() + "','" +
                    QRarray[10].trim() + "','" + QRarray[11].trim() + "','" + QRarray[12].trim() + "','" + QRarray[15].trim() + "','" +
                    tmp16 + "','" + checkCode + "','" + getDateTime2() + "')";
            mydbHelper.InsertToOfflineTickets(strSQL);
        } catch (Exception ex) {
            //reMsg = "驗票失敗\r\n發生無法預期錯誤";
        }
    }

    private void saveworkdate(String guid, String IO, String CardNo, String CurrentELCode, String checkCode) {
        String RF = guid;
        strSQL = "Insert Into WorkCardLog (DeviceID,DirectionType,SensorCode,CodeNo,Current_EL_Code,EL_CODE,Result,SenseDT)" +
                "Values('" + DeviceID + "','" + IO + "','" + RF + "','" + CardNo + "','" + CurrentELCode + "','" + EL + "','" + checkCode + "','" + getDateTime() + "')";
        mydbHelper.InsertToOfflineTickets(strSQL);
    }

    private void tickCheck() {
        setResultText(result = "票券狀態    驗票成功\n\n票券身分    參觀證\n\n票券名稱    " + mydbHelper.GetBadgeType(ary[2]) + "\n\n票券入場紀錄\n\n" + getDateTime());
    }

    // 解碼作業
    private String QRDecod(String _PacketData, String iv) {
        try {
            String newKey = "", strDecod = _PacketData;

            for (String strKey : sQRPWDItem) {
                newKey = MakeKeyLen32(strKey);
                byte[] descryptBytes = DecryptAES256(newKey.getBytes("UTF-8"), iv.getBytes("UTF-8"), Base64.decode(_PacketData, Base64.DEFAULT));
                String descryptResult = new String(descryptBytes);
                if (descryptResult.split("@").length == 17) {
                    strDecod = descryptResult;
                    break;
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
    private void setResultText(String text) {
        ResultTxt.setText(text);
    }

    //票券狀態文字
    private void setResultText2(String text) {
        ResultTxt2.setText(text);
    }

    //取得現在時間
    public String getDateTime() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Calendar c = Calendar.getInstance();
        String str = df.format(c.getTime());
        return str;
    }

    //取得現在時間
    public String getDateTime2() {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        Calendar c = Calendar.getInstance();
        String str = df.format(c.getTime());
        return str;
    }

    //震動
    public void setVibrate(int time) {
        Vibrator myVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        myVibrator.vibrate(time);
    }
}
