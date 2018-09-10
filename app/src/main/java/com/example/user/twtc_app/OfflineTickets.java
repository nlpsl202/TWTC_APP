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
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

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
    ImageView resultImage;
    TextView succeedResultTxt, failedResultTxt;
    RadioButton inRBtn, outRBtn;
    Button returnBtn, homeBtn;
    String result, DeviceID, strSQL, EL;
    String[] ary;
    String[] sQRPWDItem = new String[2];
    boolean reading = false;

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
            OfflineTickets.this.finish();
            return;
        }*/

        resultImage = (ImageView) findViewById(R.id.resultImage);
        returnBtn = (Button) findViewById(R.id.ReturnBtn);
        homeBtn = (Button) findViewById(R.id.HomeBtn);
        succeedResultTxt = (TextView) findViewById(R.id.succeedResultTxt);
        failedResultTxt = (TextView) findViewById(R.id.failedResultTxt);
        inRBtn = (RadioButton) findViewById(R.id.InRBtn);
        outRBtn = (RadioButton) findViewById(R.id.InRBtn);
        cbMgr = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        resultImage.setVisibility(View.GONE);
        inRBtn.setChecked(true);

        sQRPWDItem[0] = xmlHelper.ReadValue("Key1");
        sQRPWDItem[1] = xmlHelper.ReadValue("Key2");
        EL = xmlHelper.ReadValue("NameCode");
        DeviceID = xmlHelper.ReadValue("MachineID");

        //region 掃描事件
        mPrimaryClipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
            public void onPrimaryClipChanged() {
                try {
                    setVibrate(100);
                    resultImage.setVisibility(View.VISIBLE);
                    String qr = cbMgr.getPrimaryClip().getItemAt(0).getText().toString();
                    /*if (qr.length() < 30) {
                        resultImage.setImageResource(R.drawable.ticket_failed);
                        setFailedResultText("票劵錯誤\n無效票卡!");
                        return;
                    }else{
                        resultImage.setImageResource(R.drawable.ticket_success);
                        setSucceedResultText("票券狀態    驗票成功\n票券身分    參觀證\n票券名稱    國外買主\n票券入場紀錄\n" + getDateTime3());
                        return;
                    }*/
                    String value = qr.substring(0, qr.length() - 16);
                    String iv = qr.substring(qr.length() - 16);
                    Calendar c = Calendar.getInstance();
                    SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
                    SimpleDateFormat df2 = new SimpleDateFormat("HHmm");

                    //QRCode 狀態下不驗證服務證與工作證
                    /*if (xmlHelper.ReadValue("WorkType").toUpperCase().equals("W")) {
                        failedLayout.setVisibility(View.VISIBLE);
                        setResultText(result = "票券狀態    ");
                        setResultText2(result = "票劵錯誤，無效票卡!");
                        return;
                    }*/

                    if (qr.length() < 30) {
                        setFailedResultText("票劵錯誤\n無效票卡!");
                        return;
                    }

                    ary = QRDecod(value, iv).split("@", -1);
                    String a = ary[5];
                    if (ary.length == 17) {
                        //檢查UUID 8-4-4-4-12
                        String[] sUUID = ary[0].split("-", -1);
                        if (sUUID.length == 5) {
                            if (sUUID[0].length() != 8 || sUUID[1].length() != 4 || sUUID[2].length() != 4 || sUUID[3].length() != 4 || sUUID[4].length() != 12) {
                                setFailedResultText("票劵錯誤\n無效票卡!");
                                return;
                            }
                        }

                        if (!ary[1].trim().contains(EL)) {
                            setFailedResultText("票劵錯誤\n展覽代號不符合!");
                            savedate(ary[0].trim(), true, ary, "C");
                        } else {
                            //離線狀態下出場規則
                            if ((inRBtn.isChecked() ? "I" : "O").equals("O")) {
                                savedate(ary[0].trim(), true, ary, "A");
                                tickCheck();
                                return;
                            }

                            int iTotalINTime = 0, iTotalToDayINTime = 0;
                            //入場日期判斷
                            if (ary[3].trim().equals("A")) {//不限制入場日期
                                //直接進入下一回合
                            } else if (ary[3].trim().equals("B")) {//起始日期與截止日期相同(與系統日期)
                                if (!ary[4].equals(ary[5])) {
                                    setFailedResultText("票劵錯誤\n票券日期規則不符!");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                } else {//相同時間後比較系統時間

                                    if (!ary[4].equals(df.format(c.getTime()))) {
                                        setFailedResultText("票劵錯誤\n已過入場日期!");
                                        savedate(ary[0].trim(), true, ary, "C");
                                        return;
                                    }
                                }
                            } else if (ary[3].trim().equals("C")) {//依據起始日期與截止日期判斷
                                int tsStartDay, tsEndDay;
                                tsStartDay = Integer.parseInt(df.format(c.getTime())) - Integer.parseInt(ary[4]);
                                tsEndDay = Integer.parseInt(df.format(c.getTime())) - Integer.parseInt(ary[5]);

                                if (tsStartDay < 0) {
                                    setFailedResultText("票劵錯誤\n未到入場日期!");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                } else if (tsEndDay > 0) {
                                    setFailedResultText("票劵錯誤\n已過入場日期!");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                }
                            } else {
                                setFailedResultText("票劵錯誤\n無效票卡!");
                                savedate(ary[0].trim(), true, ary, "C");
                                return;
                            }
                            //入場時段判斷
                            if (ary[6].trim().equals("A")) {
                                //直接再進入下一回合
                            } else if (ary[6].trim().equals("B")) {
                                if ((Integer.parseInt(ary[7].substring(0, 2)) > Integer.parseInt(df2.format(c.getTime()).substring(0, 2))) ||
                                        (Integer.parseInt(ary[7].substring(0, 2)) == Integer.parseInt(df2.format(c.getTime()).substring(0, 2)) &&
                                                Integer.parseInt(ary[7].substring(2)) > Integer.parseInt(df2.format(c.getTime()).substring(2)))) {
                                    setFailedResultText("票劵錯誤\n未到入場時間!");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                } else if ((Integer.parseInt(ary[8].substring(0, 2)) < Integer.parseInt(df2.format(c.getTime()).substring(0, 2))) ||
                                        (Integer.parseInt(ary[8].substring(0, 2)) == Integer.parseInt(df2.format(c.getTime()).substring(0, 2)) &&
                                                Integer.parseInt(ary[8].substring(2)) < Integer.parseInt(df2.format(c.getTime()).substring(2)))) {
                                    setFailedResultText("票劵錯誤\n已過入場時間!");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                }
                            } else {
                                setFailedResultText("票劵錯誤\n無效票卡!");
                                savedate(ary[0].trim(), true, ary, "C");
                                return;
                            }

                            if (ary[9].length() % 2 != 0) {//入場展區必為偶數
                                setFailedResultText("票劵錯誤\n無效票卡!");
                                savedate(ary[0].trim(), true, ary, "C");
                                return;
                            }

                            //先取得相關資料
                            iTotalINTime = mydbHelper.GetLoginCount(ary[0].trim(), "");
                            iTotalToDayINTime = mydbHelper.GetLoginCount(ary[0].trim(), df.format(c.getTime()));

                            if (ary[10].trim().equals("0")) {//入場次數不限制
                                //直接再進入下一回合
                            } else if (ary[10].trim().equals("1") || ary[10].trim().equals("3")) {//限制總入場次數
                                if (Integer.parseInt(ary[11].trim()) < iTotalINTime) {
                                    setFailedResultText("票劵錯誤\n票劵已達使用上限!");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                }
                            } else if (ary[10].trim().equals("2") || ary[10].trim().equals("3")) {//限制當日入場次數
                                if (Integer.parseInt(ary[12].trim()) < iTotalToDayINTime) {
                                    setFailedResultText("票劵錯誤\n票劵已達使用上限!");
                                    savedate(ary[0].trim(), true, ary, "C");
                                    return;
                                }
                            }

                            if (ary[14].trim().equals("Y")) {//判斷是否已停用
                                setFailedResultText("票劵錯誤\n票劵已停用!");
                                savedate(ary[0].trim(), true, ary, "C");
                                return;
                            }

                            //進場
                            savedate(ary[0].trim(), true, ary, "A");
                            tickCheck();
                        }
                    } else {
                        setFailedResultText("票劵錯誤\n無效票卡!");
                    }
                } catch (Exception ex) {
                    setFailedResultText("票劵錯誤\n無效票卡!");
                    WriteLog.appendLog("OfflineTickets.java/ticket/Exception:" + ex.toString());
                }
            }
        };
        //endregion
        cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);

        returnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OfflineTickets.this.finish();
            }
        });

        //Home
        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OfflineTickets.this.finish();
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
        if (xmlHelper.ReadValue("RFID").equals("OPEN")) {
            mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (xmlHelper.ReadValue("RFID").equals("OPEN")) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
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
        resultImage.setVisibility(View.VISIBLE);
        String RFData = "";
        String tagNo = "";
        String strCardID = "", strStartDate = "", strEndDate = "", strCardName = "";
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        SimpleDateFormat sdf2 = new SimpleDateFormat("HHmm");
        String strNowDate = sdf.format(date);
        String strNowTime = sdf2.format(date);
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
            byte[][] pDatas = new byte[20][16];
            int block;
            for (int i = 0; i < 5; i++) {
                if (mfc.authenticateSectorWithKeyA(i, MifareClassic.KEY_DEFAULT)) {
                    for (int j = 0; j < 4; j++) {
                        block = i * 4 + j;
                        if (block == 0 || block == 7 || block == 11 || block == 15 || block == 19) {
                            continue;
                        }
                        pDatas[block] = mfc.readBlock(block);
                    }
                } else {

                }
            }
            //if (mfc.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT)) {
            //讀取卡別資訊
            pData = pDatas[1];
            BlockInfo.bCardType = pData[0];

            if (pData[0] == (byte) 0xFC || pData[0] == (byte) 0xFD) { //服務證與臨時證
                iBlockCount = 1; //僅讀取Block4
            } else if (pData[0] >= 0x01 && pData[0] <= 0x0F) { //參觀證或其他
                iBlockCount = pData[0];
                if (iBlockCount > 15 || iBlockCount == 0) {//區塊超出範圍
                    setFailedResultText("票劵錯誤\n無效票卡!");
                }
            }

                /*for (int iBlock = 4; iBlock < iBlockCount + 4; iBlock++) {
                    //避開 security block
                    if (iBlock == 7 || iBlock == 11 || iBlock == 15 || iBlock == 19) {
                        iBlockCount++;
                        continue;
                    }

                    if(iBlockCount==1 && (BlockInfo.bCardType == (byte) 0xFC || BlockInfo.bCardType == (byte) 0xFD)){
                        if (mfc.authenticateSectorWithKeyA(iBlock / 4, MifareClassic.KEY_DEFAULT)) {
                            pData = mfc.readBlock(iBlock);

                            try {
                                System.arraycopy(pData, 0, BlockInfo.bStartDate, 0, 3);
                                System.arraycopy(pData, 3, BlockInfo.bEndDate, 0, 3);
                                System.arraycopy(pData, 6, BlockInfo.bCardID, 0, 8);
                            } catch (Exception e) {
                                WriteLog.appendLog("OnlineTickets.java/DownloadDeviceSetup/Exception:" + e.toString());
                            }
                        }else{
                            setFailedResultText("票劵錯誤\n票卡讀取失敗!");
                            reading=false;
                            return;
                        }
                    }else{
                        if(iBlock/4==(iBlock-1)/4){
                            pData = mfc.readBlock(iBlock);
                            RFData = RFData + getHexToString(byte2hex(pData));
                        }else{
                            if (mfc.authenticateSectorWithKeyA(iBlock / 4, MifareClassic.KEY_DEFAULT)) {
                                pData = mfc.readBlock(iBlock);
                                RFData = RFData + getHexToString(byte2hex(pData));
                            }else{
                                setFailedResultText("票劵錯誤\n票卡讀取失敗!");
                                reading=false;
                                return;
                            }
                        }
                    }
                }*/

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

            ary = RFData.split("@", -1);

            if (BlockInfo.bCardType == (byte) 0xFC || BlockInfo.bCardType == (byte) 0xFD) {
                //判斷當前是否允許服務證驗證
                //if (!xmlHelper.ReadValue("WorkType").equals("W")) {
                if (!(1 == 1)) {
                    setFailedResultText("票劵錯誤\n不允許服務證驗證!");
                } else {
                    if (BlockInfo.bCardType == (byte) 0xFD) { //服務證
                        if (BlockInfo.bCardID[0] == 'A' || BlockInfo.bCardID[0] == 'G' || BlockInfo.bCardID[0] == 'N') {
                            strCardName = "服務證";
                        } else {
                            setFailedResultText("票劵錯誤\n無效服務證!");
                        }
                    }
                    if (BlockInfo.bCardType == (byte) 0xFC) { //臨時證
                        if ((BlockInfo.bCardID[0] == 'A' || BlockInfo.bCardID[0] == 'G' || BlockInfo.bCardID[0] == 'N') &&
                                (BlockInfo.bCardID[1] == 'D' || BlockInfo.bCardID[1] == 'D' || BlockInfo.bCardID[1] == 'D')) {
                            strCardName = "臨時證";
                        } else {
                            setFailedResultText("票劵錯誤\n無效臨時證!");
                        }
                    }
                    try {
                        //轉換ASCII to ANSI
                        strCardID = new String(BlockInfo.bCardID, "UTF-8");
                        strStartDate = String.format("%04d", 2000 + (BlockInfo.bStartDate[0] & 0xFF)) + String.format("%02d", BlockInfo.bStartDate[1] & 0xFF) + String.format("%02d", BlockInfo.bStartDate[2] & 0xFF);
                        strEndDate = String.format("%04d", 2000 + (BlockInfo.bEndDate[0] & 0xFF)) + String.format("%02d", BlockInfo.bEndDate[1] & 0xFF) + String.format("%02d", BlockInfo.bEndDate[2] & 0xFF);
                        int iStartDay, iEndDay;
                        iStartDay = Integer.parseInt(strNowDate) - Integer.parseInt(strStartDate);
                        iEndDay = Integer.parseInt(strNowDate) - Integer.parseInt(strEndDate);
                        //判斷當前時間與有效時間
                        if ((iStartDay < 0 || iEndDay > 0) && (inRBtn.isChecked() ? "I" : "O").equals("I")) {
                            setFailedResultText("票劵錯誤\n逾期票卡!");
                            saveworkdate(tagNo, (inRBtn.isChecked() ? "I" : "O"), strCardID, xmlHelper.ReadValue("NameCode"), "C");
                        } else {//證別有效時間正確即可
                            setSucceedResultText("票券狀態    驗票成功\n票券身分    " + strCardName + (inRBtn.isChecked() ? "\n票券入場紀錄\n" : "\n票券出場紀錄\n") + getDateTime3());
                            saveworkdate(tagNo, (inRBtn.isChecked() ? "I" : "O"), strCardID, xmlHelper.ReadValue("NameCode"), "A");
                            RFData = "";
                        }
                    } catch (Exception ex) {
                        setFailedResultText("票劵錯誤\n請重新確認!");
                    }
                }
            } else {
                if (ary.length == 17) {
                    if (!ary[1].trim().contains(EL)) {
                        setFailedResultText("票劵錯誤\n展覽代號不符合!");
                        savedate(tagNo.trim(), false, ary, "C");
                    } else {
                        //離線狀態下出場規則
                        if ((inRBtn.isChecked() ? "I" : "O").equals("O")) {
                            savedate(tagNo.trim(), false, ary, "A");
                            tickCheck();
                            reading = false;
                            return;
                        }

                        int iTotalINTime = 0, iTotalToDayINTime = 0;

                        //入場日期判斷
                        if (ary[3].trim().equals("A")) {//不限制入場日期
                            //直接進入下一回合
                        } else if (ary[3].trim().equals("B")) {//起始日期與截止日期相同(與系統日期)
                            if (ary[4].trim().equals("") || ary[5].trim().equals("")) {
                                setFailedResultText("票劵錯誤\n無效票卡!");
                                savedate(tagNo.trim(), false, ary, "C");
                                reading = false;
                                return;
                            }

                            if (!ary[4].trim().equals(ary[5].trim())) {
                                setFailedResultText("票劵錯誤\n票券已過期!");
                                savedate(tagNo.trim(), false, ary, "C");
                                reading = false;
                                return;
                            } else {
                                if (!ary[4].trim().equals(strNowDate)) {
                                    setFailedResultText("票劵錯誤\n票券已過期!");
                                    savedate(tagNo.trim(), false, ary, "C");
                                    reading = false;
                                    return;
                                }
                            }
                        } else if (ary[3].trim().equals("C")) {//依據起始日期與截止日期判斷
                            if (ary[4].trim().equals("") || ary[5].trim().equals("")) {
                                setFailedResultText("票劵錯誤\n無效票卡!");
                                savedate(tagNo.trim(), false, ary, "C");
                                reading = false;
                                return;
                            }

                            int iStartDay = 0, iEndDay = 0;
                            iStartDay = Integer.parseInt(strNowDate) - Integer.parseInt(ary[4]);
                            iEndDay = Integer.parseInt(strNowDate) - Integer.parseInt(ary[5]);

                            if (iStartDay < 0) {
                                setFailedResultText("票劵錯誤\n未到入場時間!");
                                savedate(tagNo.trim(), false, ary, "C");
                                reading = false;
                                return;
                            } else if (iEndDay > 0) {
                                setFailedResultText("票劵錯誤\n票券已過期!");
                                savedate(tagNo.trim(), false, ary, "C");
                                reading = false;
                                return;
                            }
                        } else {
                            setFailedResultText("票劵錯誤\n無效票卡!");
                            savedate(tagNo.trim(), false, ary, "C");
                            reading = false;
                            return;
                        }
                        //入場時段判斷
                        if (ary[6].trim().equals("A")) {
                            //直接再進入下一回合
                        } else if (ary[6].trim().equals("B")) {
                            //判斷是否為空值
                            if (ary[7].trim().equals("") || ary[8].trim().equals("")) {
                                setFailedResultText("票劵錯誤\n無效票卡!");
                                savedate(tagNo.trim(), false, ary, "C");
                                reading = false;
                                return;
                            }
                            if ((Integer.parseInt(ary[7].substring(0, 2)) > Integer.parseInt(strNowTime.substring(0, 2))) ||
                                    (Integer.parseInt(ary[7].substring(0, 2)) == Integer.parseInt(strNowTime.substring(0, 2)) &&
                                            Integer.parseInt(ary[7].substring(2)) > Integer.parseInt(strNowTime.substring(2)))) {
                                setFailedResultText("票劵錯誤\n未到入場時間!");
                                savedate(tagNo.trim(), false, ary, "C");
                                reading = false;
                                return;
                            } else if ((Integer.parseInt(ary[8].substring(0, 2)) < Integer.parseInt(strNowTime.substring(0, 2))) ||
                                    (Integer.parseInt(ary[8].substring(0, 2)) == Integer.parseInt(strNowTime.substring(0, 2)) &&
                                            Integer.parseInt(ary[8].substring(2)) < Integer.parseInt(strNowTime.substring(2)))) {
                                setFailedResultText("票劵錯誤\n票券已過期!");
                                savedate(tagNo.trim(), false, ary, "C");
                                reading = false;
                                return;
                            }
                        } else {
                            setFailedResultText("票劵錯誤\n無效票卡!");
                            savedate(tagNo.trim(), false, ary, "C");
                            reading = false;
                            return;
                        }

                        if (ary[9].length() % 2 != 0) //入場展區必為偶數
                        {
                            setFailedResultText("票劵錯誤\n無效票卡!");
                            savedate(tagNo.trim(), false, ary, "C");
                            reading = false;
                            return;
                        }

                        //先取得相關資料
                        iTotalINTime = mydbHelper.GetLoginCount(ary[0].trim(), "");
                        iTotalToDayINTime = mydbHelper.GetLoginCount(ary[0].trim(), strNowDate);

                        if (ary[10].trim().equals("0")) {//入場次數不限制
                            //直接再進入下一回合
                        } else if (ary[10].trim().equals("1") || ary[10].trim().equals("3")) { //限制總入場次數

                            if (Integer.parseInt(ary[11].trim()) < iTotalINTime) {
                                setFailedResultText("票劵錯誤\n票劵已達使用上限!");
                                savedate(tagNo.trim(), false, ary, "C");
                                reading = false;
                                return;
                            }
                        } else if (ary[10].trim().equals("2") || ary[10].trim().equals("3")) { //限制當日入場次數

                            if (Integer.parseInt(ary[12].trim()) < iTotalToDayINTime) {
                                setFailedResultText("票劵錯誤\n票劵已達使用上限!");
                                savedate(tagNo.trim(), false, ary, "C");
                                reading = false;
                                return;
                            }
                        } else {//入場時間型態錯誤
                            setFailedResultText("票劵錯誤\n無效票卡!");
                            savedate(tagNo.trim(), false, ary, "C");
                            reading = false;
                            return;
                        }

                        if (ary[14].trim().equals("Y"))//判斷是否已停用
                        {
                            setFailedResultText("票劵錯誤\n票劵已停用!");
                            savedate(tagNo.trim(), false, ary, "C");
                            reading = false;
                            return;
                        } else if (ary[14].trim().equals("")) {
                            setFailedResultText("票劵錯誤\n無效票卡!");
                            savedate(tagNo.trim(), false, ary, "C");
                            reading = false;
                            return;
                        }

                        //進場
                        savedate(tagNo.trim(), false, ary, "A");
                        tickCheck();
                    }
                    RFData = "";
                } else {
                    setFailedResultText("票劵錯誤\n無效票卡!" + ary.length);
                }
            }
            reading = false;
            //} else { // Authentication failed - Handle it
            //    setFailedResultText("票劵錯誤\n票卡讀取失敗!");
            //    reading=false;
            //}
        } catch (IOException ex) {
            if (ex.toString().contains("lost")) {
                setFailedResultText("票劵錯誤\n請重新感應!");
            } else {
                setFailedResultText("票劵錯誤\n無效票卡!");
            }
            WriteLog.appendLog("OfflineTickets.java/ticket/Exception:" + ex.toString());
            reading = false;
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
                    "Values('" + DeviceID + "','" + (inRBtn.isChecked() ? "I" : "O") + "','" + RF + "','" + u.toString() + "','" + QRarray[1].trim() + "','" + EL + "','" +
                    QRarray[2].trim() + "','" + QRarray[3].trim() + "','" + VP_ValidDateBegin + "','" + VP_ValidDateEnd + "','" +
                    QRarray[6].trim() + "','" + QRarray[7].trim() + "','" + QRarray[8].trim() + "','" + QRarray[9].trim() + "','" +
                    QRarray[10].trim() + "','" + QRarray[11].trim() + "','" + QRarray[12].trim() + "','" + QRarray[15].trim() + "','" +
                    tmp16 + "','" + checkCode + "','" + getDateTime() + "')";
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
        setSucceedResultText("票券狀態    驗票成功\n票券身分    參觀證\n票券名稱    " + mydbHelper.GetBadgeType(ary[2]) + (inRBtn.isChecked() ? "\n票券入場紀錄\n" : "\n票券出場紀錄\n") + getDateTime3());
    }

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
    private void setSucceedResultText(String text) {
        succeedResultTxt.setVisibility(View.VISIBLE);
        failedResultTxt.setVisibility(View.GONE);
        succeedResultTxt.setText(text);
        resultImage.setImageResource(R.drawable.ticket_success);
    }

    //票券狀態文字
    private void setFailedResultText(String text) {
        failedResultTxt.setVisibility(View.VISIBLE);
        succeedResultTxt.setVisibility(View.GONE);
        failedResultTxt.setText(text);
        resultImage.setImageResource(R.drawable.ticket_failed);
    }


    //票券狀態文字
    private void setResultText(String text) {
        //    resultTxt.setText(text);
    }

    //票券狀態文字
    private void setResultText2(String text) {
        //    resultTxt2.setText(text);
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

    //取得現在時間
    public String getDateTime3() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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
