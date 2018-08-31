package com.example.user.twtc_app;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Jeff.
 */
public class BluetoothTickets extends Activity {
    private String result, qr, PCReturnData, restr = "", sWorkCardNo;
    private ImageView photoImage;
    private TextView resultTxt, resultTxt2, resultTxt3;
    private Button returnBtn, homeBtn;
    private RadioButton inRBtn, outRBtn;
    private LinearLayout wifiLayout, rfidLayout, failedLayout;

    //圖片
    private byte[] bPicBuff = new byte[20480];
    private Boolean bFinishPic = false, Rec = false, bWaitPic = false;

    //剪貼簿
    private ClipboardManager cbMgr;
    private ClipboardManager.OnPrimaryClipChangedListener mPrimaryClipChangedListener;

    //RFID
    private NfcAdapter mNfcAdapter;
    private PendingIntent mPendingIntent;
    private Bitmap bitmap;

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

    //參數XML
    private XmlHelper xmlHelper;

    //藍牙
    private OutputStream mmOutputStream;
    private InputStream mmInputStream;
    private Thread workerThread;
    private byte[] readBuffer;
    private int readBufferPosition;
    private volatile boolean stopWorker;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.bluetooth_tickets);

        //判斷藍牙現在是否連接中
        if (BluetoothConnectSetting.connectedBluetoothDevices.size() < 1) {
            BluetoothTickets.this.finish();
        }

        returnBtn = (Button) findViewById(R.id.ReturnBtn);
        homeBtn = (Button) findViewById(R.id.HomeBtn);
        inRBtn = (RadioButton) findViewById(R.id.InRBtn);
        outRBtn = (RadioButton) findViewById(R.id.OutRBtn);
        resultTxt = (TextView) findViewById(R.id.ResultTxt);
        resultTxt2 = (TextView) findViewById(R.id.ResultTxt2);
        resultTxt3 = (TextView) findViewById(R.id.ResultTxt3);
        photoImage = (ImageView) findViewById(R.id.FtPhotoImage);
        wifiLayout = (LinearLayout) findViewById(R.id.wifiLayout);
        rfidLayout = (LinearLayout) findViewById(R.id.rfidLayout);
        failedLayout = (LinearLayout) findViewById(R.id.FailedLayout);
        cbMgr = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        inRBtn.setChecked(true);

        xmlHelper = new XmlHelper(getFilesDir() + "//connectData.xml");

        //設定藍牙收發資料
        try {
            setBT();
        } catch (Exception ex) {
            ex.printStackTrace();
            WriteLog.appendLog("BluetoothTickets.java/setBT/Exception:" + ex.toString());
        }

        //掃描事件
        mPrimaryClipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
            public void onPrimaryClipChanged() {
                try {
                    String retMsg = "";//回傳訊息
                    String strCardName = "";//回傳訊息
                    String strCardType = "";

                    failedLayout.setVisibility(View.GONE);
                    wifiLayout.setVisibility(View.VISIBLE);
                    rfidLayout.setVisibility(View.GONE);

                    setVibrate(100);
                    qr = cbMgr.getPrimaryClip().getItemAt(0).getText().toString();
                    restr = sendData("Q|" + (inRBtn.isChecked() ? "I|" : "O|") + qr);
                    if (!restr.equals("ERROR") && restr.length() != 0) {
                        //接收格式如下
                        //驗證狀態|票劵代碼
                        String[] tempstr = restr.split("\\|",-1);

                        if (tempstr[0].equals("V")) {
                            retMsg = tempstr[1];//回傳訊息
                            strCardName = tempstr[2];//回傳訊息
                            strCardType = "";
                            if (retMsg.length() != 0) {
                                strCardType = "參觀證";
                            } else {
                                retMsg = "無法取得回傳訊息-請重新確認";
                            }
                        } else {
                            failedLayout.setVisibility(View.VISIBLE);
                            setResultText("票券狀態    ");
                            setResultText2("回傳訊息解析錯誤-V");
                        }
                    } else {
                        retMsg = "無法取得回傳訊息-請重新確認";
                    }

                    if (retMsg.contains("成功")) {
                        failedLayout.setVisibility(View.GONE);
                        setResultText("票券狀態    " + retMsg + "\n\n票券身分    " + strCardType + "\n\n票券名稱    " + strCardName + (inRBtn.isChecked() ? "\n\n票券入場紀錄\n\n" : "\n\n票券出場紀錄\n\n") + getDateTime());
                    } else {
                        failedLayout.setVisibility(View.VISIBLE);
                        setResultText("票券狀態    ");
                        setResultText2(retMsg);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    failedLayout.setVisibility(View.VISIBLE);
                    setResultText("票券狀態    ");
                    setResultText2("票劵錯誤\r\n(異常)請重新確認");
                    WriteLog.appendLog("BluetoothTickets.java/ticket/Exception:" + ex.toString());
                }
            }
        };
        cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);

        //回上頁
        returnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                workerThread.interrupt();
                BluetoothTickets.this.finish();
            }
        });

        //Home
        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                workerThread.interrupt();
                BluetoothTickets.this.finish();
            }
        });

        //RFID
        if (xmlHelper.ReadValue("RFID").equals("OPEN")) {
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (mNfcAdapter == null) {
                Toast.makeText(BluetoothTickets.this, "此裝置無NFC功能，無法進行RFID驗票！", Toast.LENGTH_SHORT).show();
                return;
            } else {
                if (!mNfcAdapter.isEnabled()) {
                    Toast.makeText(BluetoothTickets.this, "NFC功能尚未開啟，請至設定開啟！", Toast.LENGTH_SHORT).show();
                }
            }
            mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                    getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        }

        //監聽藍芽狀態
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mReceiver, filter);
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
        unregisterReceiver(mReceiver);
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
    }

    //RFID感應事件
    @Override
    protected void onNewIntent(Intent intent) {
        getTagInfo(intent);
    }

    //RFID驗票
    private void getTagInfo(Intent intent) {
        //清空圖像資訊
        bitmap = null;
        photoImage.setImageBitmap(bitmap);

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
        MifareClassic mfc = MifareClassic.get(tag);

        try {
            mfc.connect();
            boolean auth = false;

            auth = mfc.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT);
            if (auth) {
                //讀取卡別資訊
                pData = mfc.readBlock(1);
                BlockInfo.bCardType = pData[0];
                if (pData[0] == (byte) 0xFC || pData[0] == (byte) 0xFD) { //服務證與臨時證
                    iBlockCount = 1; //僅讀取Block4
                } else if (pData[0] >= (byte) 0x01 && pData[0] <= (byte) 0x0F) { //一般證別
                    iBlockCount = pData[0];
                } else { //其他證別(Mifare)
                    iBlockCount = 0;
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
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                WriteLog.appendLog("BluetoothTickets.java/getTagInfo/Exception:" + ex.toString());
                            }
                        }
                    }
                }

                String ary[] = RFData.split("@", -1);

                if (BlockInfo.bCardType == (byte) 0xFC || BlockInfo.bCardType == (byte) 0xFD) {
                    wifiLayout.setVisibility(View.GONE);
                    rfidLayout.setVisibility(View.VISIBLE);
                    if (BlockInfo.bCardType == (byte) 0xFD) {//服務證
                        if (BlockInfo.bCardID[0] == 'A' || BlockInfo.bCardID[0] == 'G' || BlockInfo.bCardID[0] == 'N') {
                            //do nothing
                            strCardName = "服務證";
                        } else {
                            //清空圖像資訊
                            bitmap = null;
                            photoImage.setImageBitmap(bitmap);
                            setResultText3("票券狀態    \n票劵錯誤\n\n(格式)無效服務證");
                        }
                    }
                    if (BlockInfo.bCardType == (byte) 0xFC) { //臨時證
                        if ((BlockInfo.bCardID[0] == 'A' || BlockInfo.bCardID[0] == 'G' || BlockInfo.bCardID[0] == 'N') &&
                                (BlockInfo.bCardID[1] == 'D' || BlockInfo.bCardID[1] == 'D' || BlockInfo.bCardID[1] == 'D')) {
                            //do nothing
                            strCardName = "臨時證";
                        } else {
                            bitmap = null;
                            photoImage.setImageBitmap(bitmap);
                            setResultText3("票券狀態    \n票劵錯誤\n\n(格式)無效臨時證");
                        }
                    }

                    try {
                        //轉換ASCII to ANSI
                        strCardID = new String(BlockInfo.bCardID, "UTF-8");
                        strStartDate = String.format("%04d", 2000 + (BlockInfo.bStartDate[0] & 0xFF)) + String.format("%02d", BlockInfo.bStartDate[1] & 0xFF) + String.format("%02d", BlockInfo.bStartDate[2] & 0xFF);
                        strEndDate = String.format("%04d", 2000 + (BlockInfo.bEndDate[0] & 0xFF)) + String.format("%02d", BlockInfo.bEndDate[1] & 0xFF) + String.format("%02d", BlockInfo.bEndDate[2] & 0xFF);
                        Date date = Calendar.getInstance().getTime();
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                        String strNowDate = sdf.format(date);
                        //判斷當前時間與有效時間
                        if (Integer.parseInt(strNowDate) < Integer.parseInt(strStartDate) || Integer.parseInt(strNowDate) > Integer.parseInt(strEndDate)) {
                            //清空圖像資訊
                            bitmap = null;
                            photoImage.setImageBitmap(bitmap);
                            setResultText3("票券狀態    \n票劵錯誤\n\n(逾期)無效票卡");
                        } else {//向中央取得權限
                            restr = sendData("R!" + (inRBtn.isChecked() ? "I" : "O") + "!" + String.valueOf(BlockInfo.bCardType < 0 ? BlockInfo.bCardType + 256 : BlockInfo.bCardType) + "!" + tagNo + "!" + strCardID); //CardNo
                            if (!restr.equals("ERROR") && restr.length() != 0) {
                                String[] tempstr = restr.split("\\|",-1);

                                if (tempstr[0].equals("S")) {
                                    String retMsg = tempstr[1];//回傳訊息
                                    int iPicSize = Integer.parseInt(tempstr[2]);
                                    int iTotalCount = Integer.parseInt(tempstr[3]);

                                    if (retMsg.length() != 0) {
                                        byte[] ShowPicBuff = new byte[iPicSize];

                                        if (retMsg.contains("成功")) {
                                            //載入服務證ID
                                            sWorkCardNo = strCardID;

                                            try {
                                                ////取圖
                                                if (bPicBuff != null && bFinishPic) {
                                                    System.arraycopy(bPicBuff, 0, ShowPicBuff, 0, iPicSize);
                                                    bitmap = BitmapFactory.decodeByteArray(ShowPicBuff, 0, ShowPicBuff.length);
                                                    photoImage.setImageBitmap(bitmap);
                                                } else {
                                                    photoImage.setImageResource(R.drawable.failed_photo);
                                                }
                                            } catch (Exception ex) {//錯誤過程顯示預設圖像
                                                ex.printStackTrace();
                                                photoImage.setImageResource(R.drawable.failed_photo);
                                                WriteLog.appendLog("BluetoothTickets.java/getTagInfoPhoto/Exception:" + ex.toString());
                                            }
                                        } else {
                                            bWaitPic = false;//結束取圖
                                        }
                                    } else {
                                        bWaitPic = false;//結束取圖
                                        //清空圖像資訊
                                        bitmap = null;
                                        photoImage.setImageBitmap(bitmap);
                                        retMsg = "訊息錯誤\r\n(異常)無法接收訊息";
                                    }
                                    setResultText3("票券狀態    \n" + retMsg + "\n\n票券身分    " + strCardName + (inRBtn.isChecked() ? "\n\n票券入場紀錄\n\n" : "\n\n票券出場紀錄\n\n") + getDateTime());
                                } else {
                                    bWaitPic = false;//結束取圖
                                    //清空圖像資訊
                                    bitmap = null;
                                    photoImage.setImageBitmap(bitmap);
                                    setResultText3("票券狀態    \n訊息錯誤\r\n接收資料解析錯誤(S)");
                                }
                            } else if (restr.equals("ERROR")) {//ERROR
                                bWaitPic = false;//結束取圖
                                //清空圖像資訊
                                bitmap = null;
                                photoImage.setImageBitmap(bitmap);
                                setResultText3("票券狀態    \n訊息錯誤\r\n接收資料逾時(T)");
                            }
                            RFData = "";
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        //清空圖像資訊
                        bitmap = null;
                        photoImage.setImageBitmap(bitmap);
                        setResultText3("票券狀態    \n票劵錯誤\r\n(異常)請重新確認");
                        WriteLog.appendLog("BluetoothTickets.java/getTagInfo/Exception:" + ex.toString());
                    }
                } else {
                    try {
                        wifiLayout.setVisibility(View.VISIBLE);
                        rfidLayout.setVisibility(View.GONE);
                        bitmap = null;

                        //1.驗票成功，2.驗票失敗，3.未到入場時間，4.票劵已過期
                        //傳送格式如下:
                        //Command R (1 Byte) + IO(1 Byte) + CardType(1 Byte) + Uid(20 Bytes) + RFData(n Bytes)
                        restr = sendData("R!" + (inRBtn.isChecked() ? "I" : "O") + "!" + Integer.toHexString(BlockInfo.bCardType & 0xFF) + "!" + tagNo.toUpperCase() + "!" + RFData);//pUids
                        if (!restr.equals("ERROR") && restr.length() != 0) {
                            //回傳格式如下:
                            //S|Command G 驗證結果 | CodeName |
                            String[] tempstr = restr.split("\\|",-1);
                            if (tempstr[0].equals("V")) {
                                String retMsg = tempstr[1];//回傳訊息
                                strCardName = tempstr[2];//回傳訊息
                                String strCardType = "";
                                if (retMsg.length() > 0) {
                                    strCardType = "參觀證";
                                } else {
                                    retMsg = "訊息錯誤\r\n(異常)無法接收訊息";
                                }
                                if (retMsg.contains("成功")) {
                                    failedLayout.setVisibility(View.GONE);
                                    setResultText("票券狀態    " + retMsg + "\n\n票券身分    " + strCardType + "\n\n票券名稱    " + strCardName + (inRBtn.isChecked() ? "\n\n票券入場紀錄\n\n" : "\n\n票券出場紀錄\n\n") + getDateTime());
                                } else {
                                    failedLayout.setVisibility(View.VISIBLE);
                                    setResultText("票券狀態    ");
                                    setResultText2(retMsg);
                                }
                            } else {
                                failedLayout.setVisibility(View.VISIBLE);
                                setResultText("票券狀態    ");
                                setResultText2("訊息錯誤\r\n接收資料解析錯誤(V)");
                            }
                        } else if (restr.equals("ERROR")) {
                            failedLayout.setVisibility(View.VISIBLE);
                            setResultText("票券狀態    ");
                            setResultText2("訊息錯誤\r\n接收資料逾時(T)");
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        WriteLog.appendLog("BluetoothTickets.java/getTagInfo/Exception:" + ex.toString());
                        failedLayout.setVisibility(View.VISIBLE);
                        setResultText("票券狀態    ");
                        setResultText2("票劵錯誤\r\n(異常)請重新確認");
                    }
                    RFData = "";
                    bFinishPic = false;
                }
            } else { // Authentication failed - Handle it

            }
        } catch (IOException ex) {
            ex.printStackTrace();
            WriteLog.appendLog("BluetoothTickets.java/getTagInfo/Exception:" + ex.toString());
        }
    }

    //設定藍牙收發資料
    void setBT() throws IOException {
        mmOutputStream = BluetoothConnectSetting.mmSocket.getOutputStream();
        mmInputStream = BluetoothConnectSetting.mmSocket.getInputStream();
        beginListenForData();
    }

    //透過藍牙發送資料
    private String sendData(String data) {
        if (BluetoothConnectSetting.connectedBluetoothDevices.size() >= 1) {
            PCReturnData = "";
            String[] strArrary;
            bWaitPic = true;
            bFinishPic = false;
            try {
                mmOutputStream.write(data.getBytes());
                int WiatCount = 0;
                while (bWaitPic && !bFinishPic) {
                    WiatCount++;
                    Thread.sleep(10);
                    if (WiatCount >= 300) { //等300次
                        bWaitPic = false;
                        Toast.makeText(BluetoothTickets.this, "感應失敗，請重新感應！", Toast.LENGTH_SHORT).show();
                    }
                }
                return PCReturnData;
            } catch (InterruptedException ex) {
                ex.printStackTrace();
                WriteLog.appendLog("BluetoothTickets.java/sendData/" + ex.toString());
                return "ERROR";
            } catch (IOException ex) {
                ex.printStackTrace();
                WriteLog.appendLog("BluetoothTickets.java/sendData/" + ex.toString());
                return "ERROR";
            } catch (Exception ex) {
                ex.printStackTrace();
                WriteLog.appendLog("BluetoothTickets.java/sendData/" + ex.toString());
                return "ERROR";
            }
                /*Rec = true;
                int WiatCount = 0;
                while (Rec)
                {
                    WiatCount++;
                    Thread.sleep(5);

                    //判斷是否等待圖像回傳
                    if (bWaitPic)
                    {
                        //圖片接收完成與文字訊息接收完成
                        if (bFinishPic && PCReturnData.length() != 0)
                        {
                            return PCReturnData;
                        }
                        else if (!bFinishPic)//圖片接收逾時判斷文字訊息接收狀態
                        {
                            if (WiatCount >= 300)//判斷等待時間是否逾時
                            {
                                return PCReturnData;
                            }
                            else
                            {
                                //判斷是否成功取得回傳字串
                                if (PCReturnData.length() != 0)
                                {
                                    strArrary = PCReturnData.split("\\|");

                                    if (!PCReturnData.contains("成功") && bFinishPic)
                                    {
                                        return PCReturnData;
                                    }
                                    if (strArrary.length == 4)
                                    {
                                        //確認圖像是否存在
                                        if (strArrary[2] == "0")
                                        {
                                            return PCReturnData;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else
                    {
                        if (PCReturnData.length() != 0)
                        {
                            Rec = false;
                            return PCReturnData;
                        }
                        else if (WiatCount >= 300)
                        {
                            Rec = false;
                            PCReturnData = "";
                        }
                    }
                }
                return "ERROR";
            }
            catch (InterruptedException ex)
            {
                WriteLog.appendLog("BluetoothTickets.java/sendData/"+ex.toString());
                return "ERROR";
            }
            catch (IOException ex)
            {
                WriteLog.appendLog("BluetoothTickets.java/sendData/"+ex.toString());
                return "ERROR";
            }
            } catch (Exception ex) {
                ex.printStackTrace();
                WriteLog.appendLog("BluetoothTickets.java/sendData/" + ex.toString());
                return "ERROR";
            }*/
        } else {
            return "ERROR";
        }
    }

    //接收透過藍牙所傳過來的資料
    void beginListenForData() {
        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            if (packetBytes[0] != (byte) 0x66) { //非圖片訊息
                                PCReturnData += new String(packetBytes, "UTF-8");
                                if (PCReturnData.contains("成功") && (BlockInfo.bCardType == (byte) 0xFD || BlockInfo.bCardType == (byte) 0xFC)) {
                                    bWaitPic = true;
                                } else {
                                    bWaitPic = false;
                                }
                            } else {//圖片訊息
                                bWaitPic = true;
                                if (packetBytes[2] < packetBytes[1]) {
                                    System.arraycopy(packetBytes, 4, bPicBuff, packetBytes[2] * 512, 512);
                                    if (packetBytes[1] == packetBytes[2] + 1) { //最後一封包
                                        bFinishPic = true;
                                        bWaitPic = false;
                                    }
                                }
                            }
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        WriteLog.appendLog("BluetoothTickets.java/beginListenForData/Exception:" + ex.toString());
                        stopWorker = true;
                    }
                }
            }
        });
        workerThread.start();
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

    //驗票結果訊息
    private void setResultText(String text) {
        resultTxt.setText(text);
    }

    //驗票結果訊息
    private void setResultText2(String text) {
        resultTxt2.setText(text);
    }

    //驗票結果訊息
    private void setResultText3(String text) {
        resultTxt3.setText(text);
    }

    //取得現在時間 yyyy-MM-dd HH:mm:ss
    public String getDateTime() {
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

    //監聽藍芽連接狀態的廣播
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothConnectSetting.connectedBluetoothDevices.clear();
                Toast.makeText(BluetoothTickets.this, "藍牙連線關閉", Toast.LENGTH_SHORT).show();
                BluetoothTickets.this.finish();
            }
        }
    };
}
