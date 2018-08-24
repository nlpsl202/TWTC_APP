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
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import net.sourceforge.jtds.jdbc.DateTime;

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
    private static final String key="SET31275691$00000000000000000000";//QRCODE解碼KEY

    private TextView ResultTxt,ResultTxt2,ResultTxt3;
    private Button ReturnBtn,HomeBtn;
    private ImageView photoImage;
    private RadioButton InRBtn,OutRBtn;
    private LinearLayout FailedLayout,wifiLayout,rfidLayout;
    private String result="",strUserTicketName,reMsg;
    private String[] ary;
    private int UDSTime = 30;

    //SQLITE
    private MyDBHelper mydbHelper;

    //剪貼簿
    private ClipboardManager cbMgr;
    private ClipboardManager.OnPrimaryClipChangedListener mPrimaryClipChangedListener;

    //RFID
    NfcAdapter mNfcAdapter;
    PendingIntent mPendingIntent;
    Bitmap bitmap;
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

    //XML
    XmlHelper xmlHelper;

    // QRCODE 解碼密碼陣列
    String[] sQRPWDItem = { "", "" };

    //自動回報設備狀態
    private static long mainTimerInterval = 300000; //預設300秒
    private Handler handler = new Handler();

    //連接中央SQL
    private Connection DBCDPSConnection() {
        ConnectionClass.ip=xmlHelper.ReadValue("ServerIP");
        ConnectionClass.db="TWTC_CDPS";
        ConnectionClass.un=xmlHelper.ReadValue("sa");
        ConnectionClass.password=xmlHelper.ReadValue("SQLPassWord");
        Connection con = ConnectionClass.CONN();
        return con;
    }

    //連接展覽VMSQL
    private Connection DBExhibitConnection() {
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
        setContentView(R.layout.online_tickets);

        ResultTxt=(TextView) findViewById(R.id.ResultTxt);
        ResultTxt2=(TextView) findViewById(R.id.ResultTxt2);
        ResultTxt3=(TextView) findViewById(R.id.ResultTxt3);
        ReturnBtn=(Button)findViewById(R.id.ReturnBtn);
        HomeBtn=(Button)findViewById(R.id.HomeBtn);
        InRBtn=(RadioButton) findViewById(R.id.InRBtn);
        OutRBtn=(RadioButton)findViewById(R.id.OutRBtn);
        FailedLayout=(LinearLayout) findViewById(R.id.FailedLayout);
        photoImage=(ImageView) findViewById(R.id.FtPhotoImage);
        wifiLayout=(LinearLayout) findViewById(R.id.wifiLayout);
        rfidLayout=(LinearLayout) findViewById(R.id.rfidLayout);

        InRBtn.setChecked(true);

        mydbHelper = new MyDBHelper(this);
        xmlHelper=new XmlHelper(getFilesDir()+"//connectData.xml");

        ResultSet rs=DownloadDeviceSetup();
        try {
            while (rs.next()) {
                if (rs.getString("DL_IsInUse").equals("N"))
                {
                    Toast.makeText(OnlineTickets.this, "此設備停用中，無法操作", Toast.LENGTH_SHORT).show();
                    return;
                }
                String TmpName = rs.getString("EL_Code");
                String worktype = rs.getString("Work_Type");
                if(worktype!=null)
                {
                    if (worktype.trim() != "")
                    {
                        xmlHelper.WriteValue("WorkType",worktype);
                    }
                    else//無法取得相關資訊時利用前次
                    {
                        worktype = xmlHelper.ReadValue("WorkType");
                    }
                }

                //取得服務證驗證狀態
                String INCF_Type = DownloadDeviceType();
                if (INCF_Type.trim() != "")
                {
                    xmlHelper.WriteValue("IDCF",INCF_Type);
                }
                else
                {
                    INCF_Type = xmlHelper.ReadValue("IDCF");
                }
                xmlHelper.WriteValue("UDSTime",Integer.toString(UDSTime));

                ResultSet rs2 = DownloadAllExhibition(TmpName);
                while (rs2.next())
                {
                    if (rs2.getString("EL_Code").equals(TmpName) )
                    {
                        String IP = rs2.getString("EL_DB_IP");
                        String Name = rs2.getString("EL_Name");
                        String sa = rs2.getString("EL_DB_Account");
                        String pass = rs2.getString("EL_DB_PWD");

                        xmlHelper.WriteValue("VMSQlIP",IP);
                        xmlHelper.WriteValue("VMSQlsa",sa);
                        xmlHelper.WriteValue("VMSQlpass",pass);
                        xmlHelper.WriteValue("Name",Name);
                        xmlHelper.WriteValue("NameCode",TmpName);
                    }
                }
                //向VM取得當前票劵狀態名稱
                ResultSet rs3 = DownloadBadgeType();
                mydbHelper.DeleteBadgeType();
                mydbHelper.InsertToBadgeType(rs3);
            }
        }
        catch (Exception ex) {
        }

        sQRPWDItem = DownloadExhibitionSetup();

        if (sQRPWDItem == null)
        {
            Toast.makeText(OnlineTickets.this, "無法取得QRCode相關資訊，請重新連線!", Toast.LENGTH_SHORT).show();
            finish();
        }
        xmlHelper.WriteValue("Key1", sQRPWDItem[0]);
        xmlHelper.WriteValue("Key2", sQRPWDItem[1]);
        //啟動計時Threading
        if(!xmlHelper.ReadValue("UDSTime").equals("") && xmlHelper.ReadValue("UDSTime")!=null){
            mainTimerInterval = (long)(Integer.parseInt(xmlHelper.ReadValue("UDSTime")) * 1000);
        }
        //mTimer = new Timer();
        //setTimerTask(0,mainTimerInterval);
        //設定定時要執行的方法
        handler.removeCallbacks(updateTimer);
        //設定Delay的時間
        handler.postDelayed(updateTimer, 5000);


        //掃描事件
        cbMgr=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        mPrimaryClipChangedListener =new ClipboardManager.OnPrimaryClipChangedListener(){
            public void onPrimaryClipChanged() {
                try{
                    wifiLayout.setVisibility(View.VISIBLE);
                    rfidLayout.setVisibility(View.GONE);
                    setVibrate(100);
                    if (ary != null) ary=new String[ary.length];
                    //if(xmlHelper.ReadValue("WorkType").equals("W")){
                    //    setResultText(result = "票劵錯誤，無效票卡");
                    //    return;
                    //}

                    String qr=cbMgr.getPrimaryClip().getItemAt(0).getText().toString();
                    //判斷是否為1D條碼
                    if (qr.length() <= 32 && qr.substring(0, 4).toUpperCase() == "TWTC" || qr.length() == 16) {
                        BarCodeCheck(qr);
                        return;
                    }

                    String value=qr.substring(0,qr.length()-16);
                    String iv=qr.substring(qr.length() - 16);
                    ary = QRDecod(value,iv).split("@");

                    if (ary.length == 17) {
                        //檢查UUID 8-4-4-4-12
                        String[] sUUID = ary[0].toString().split("-");
                        if (sUUID.length == 5)
                        {
                            if (sUUID[0].length() != 8 || sUUID[1].length() != 4 || sUUID[2].length() != 4 || sUUID[3].length() != 4 || sUUID[4].length() != 12) {
                                //labChang(false);
                                //lab5.Text = "票劵錯誤\n(格式)無效票卡";
                                //lab6.Text = "";
                                //lab7.Text = "";
                                //lab8.Text = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                                setResultText(result = "票劵錯誤\n(格式)無效票卡");
                                return;
                            }
                        }
                        tickCheck();
                    }
                    else
                    {
                        //labChang(false);
                        //lab5.Text = "票劵錯誤\r\n(格式)無效票卡";
                        //lab6.Text = "";
                        //lab7.Text = "";
                        //lab8.Text = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                        setResultText(result = "票劵錯誤\n(格式)無效票卡");
                    }


                }catch(Exception ex){
                    setResultText(result = "票劵錯誤\n(格式)無效票卡");
                    //ResultTxt.setTextColor(Color.RED);
                }
            }
        };
        cbMgr.addPrimaryClipChangedListener(mPrimaryClipChangedListener);

        //回上頁
        ReturnBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        //Home
        HomeBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        //RFID
        if (xmlHelper.ReadValue("RFID").equals("OPEN"))
        {
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (mNfcAdapter == null)
            {
                Toast.makeText(OnlineTickets.this, "此裝置無NFC功能，無法進行RFID驗票！", Toast.LENGTH_SHORT).show();
                return;
            }
            else
            {
                if (!mNfcAdapter.isEnabled())
                {
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
    }//End ON CREATE

    @Override
    protected void onResume() {
        super.onResume();
        mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
    }

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

    //RFID感應事件
    @Override
    protected void onNewIntent(Intent intent) {
        getTagInfo(intent);
    }

    //RFID驗票
    private void getTagInfo(Intent intent) {
        String RFData = "";
        String tagNo="";
        String strCardID = "" , strStartDate = "" , strEndDate = "" , strCardName = "";
        DateTime dtStartDate , dtEndDate;
        int iBlockCount = 0;
        byte[] pData;

        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        byte[] tagId = tag.getId();
        for(int i=0; i<tagId.length; i++){
            if(Integer.toHexString(tagId[i] & 0xFF).length()<2){
                tagNo +="0";
            }
            tagNo += Integer.toHexString(tagId[i] & 0xFF);
        }
        MifareClassic mfc = MifareClassic.get(tag);

        try {
            mfc.connect();
            boolean auth = false;

            auth = mfc.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT);
            if(auth){
                //讀取卡別資訊
                pData = mfc.readBlock(1);

                if (pData[0] == (byte)0xFC  || pData[0] == (byte)0xFD) { //服務證與臨時證
                    BlockInfo.bCardType = pData[0];
                    iBlockCount = 1; //僅讀取Block4
                } else if (pData[0] >= 0x01 && pData[0] <= 0x0F){ //一般證別
                    iBlockCount = pData[0];
                } else{ //其他證別(Mifare)
                    //清空圖像資訊
                    bitmap = null;
                    photoImage.setImageBitmap(bitmap);

                    if (UpdateImportPass(tagNo, false, "") == true)
                    {
                        setResultText(result = "參觀證"+reMsg);
                        //labChang(true);
                        //lab5.Text = reMsg;
                        //lab6.Text = "參觀證";
                        //lab7.Text = strUserTicketName;
                        //lab8.Text = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                    }
                    else
                    {
                        setResultText(result = "參觀證"+reMsg);
                        //lab5.Text = reMsg;
                        //lab6.Text = "參觀證";
                        //lab7.Text = strUserTicketName;
                        //lab8.Text = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                    }
                    RFData = "";
                }

                for (int iBlock = 4; iBlock < iBlockCount + 4; iBlock++) {
                    //避開 security block
                    if (iBlock == 7 || iBlock == 11 || iBlock == 15 || iBlock == 19) {
                        iBlockCount++;
                        continue;
                    }
                    if(mfc.authenticateSectorWithKeyA(iBlock/4, MifareClassic.KEY_DEFAULT)){
                        pData = mfc.readBlock(iBlock);
                        RFData = RFData + getHexToString(byte2hex(pData));

                        if (BlockInfo.bCardType == (byte)0xFC  || BlockInfo.bCardType == (byte)0xFD) //定義第四區格式
                        {
                            try{
                                System.arraycopy(pData, 0, BlockInfo.bStartDate, 0, 3);
                                System.arraycopy(pData, 3, BlockInfo.bEndDate, 0, 3);
                                System.arraycopy(pData, 6, BlockInfo.bCardID, 0, 8);
                            }catch(Exception e){
                                WriteLog.appendLog("OnlineTickets.java/DownloadDeviceSetup/Exception:" + e.toString());
                            }
                        }
                    }
                }

                ary = RFData.split("@");

                if (BlockInfo.bCardType == (byte)0xFC  || BlockInfo.bCardType == (byte)0xFD) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            wifiLayout.setVisibility(View.GONE);
                            rfidLayout.setVisibility(View.VISIBLE);
                        }
                    });
                    //判斷當前是否允許服務證驗證
                    //if (!xmlHelper.ReadValue("WorkType").equals("W")) {
                    if (!(1==1)) {
                        //清空圖像資訊
                        bitmap = null;
                        photoImage.setImageBitmap(bitmap);

                        setResultText(result = "驗證模式\r\n不允許服務證驗證");
                        //labChang(false);
                        //lab5.Text = "驗證模式\r\n不允許服務證驗證";
                        //lab6.Text = "";
                        //lab7.Text = "";
                        //txtQR.Focus();
                        //txtQR.SelectAll();
                    } else {
                        if (BlockInfo.bCardType == (byte)0xFD){ //服務證
                            if (BlockInfo.bCardID[0] == 'A' || BlockInfo.bCardID[0] == 'G' || BlockInfo.bCardID[0] == 'N') {
                                //do nothing
                                strCardName = "服務證";
                            } else {
                                //清空圖像資訊
                                bitmap = null;
                                photoImage.setImageBitmap(bitmap);
                                setResultText(result = "票劵錯誤\r\n(格式)無效服務證");
                                //labChang(false);
                                //lab5.Text = "票劵錯誤\r\n(格式)無效服務證";
                                //lab6.Text = "";
                                //lab7.Text = "";
                                //txtQR.Focus();
                                //txtQR.SelectAll();
                            }
                        }
                        if (BlockInfo.bCardType == (byte)0xFC){ //臨時證
                            if ((BlockInfo.bCardID[0] == 'A' || BlockInfo.bCardID[0] == 'G' || BlockInfo.bCardID[0] == 'N') &&
                                (BlockInfo.bCardID[1] == 'D' || BlockInfo.bCardID[1] == 'D' || BlockInfo.bCardID[1] == 'D')) {
                                //do nothing
                                strCardName = "臨時證";
                            }
                            else
                            {
                                //清空圖像資訊
                                bitmap = null;
                                photoImage.setImageBitmap(bitmap);
                                setResultText(result = "票劵錯誤\r\n(格式)無效臨時證");
                                //labChang(false);
                                //lab5.Text = "票劵錯誤\r\n(格式)無效臨時證";
                                //lab6.Text = "";
                                //lab7.Text = "";
                                //txtQR.Focus();
                                //txtQR.SelectAll();
                            }
                        }
                        try {
                            //轉換ASCII to ANSI
                            strCardID = new String(BlockInfo.bCardID, "UTF-8");
                            strStartDate = String.format("%04d",2000 + (BlockInfo.bStartDate[0] & 0xFF)) + String.format("%02d",BlockInfo.bStartDate[1] & 0xFF) + String.format("%02d",BlockInfo.bStartDate[2] & 0xFF);
                            strEndDate = String.format("%04d",2000 + (BlockInfo.bEndDate[0] & 0xFF)) + String.format("%02d",BlockInfo.bEndDate[1] & 0xFF) + String.format("%02d",BlockInfo.bEndDate[2] & 0xFF);
                            Date date = new Date();
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                            String dateString = sdf.format(date);
                            //判斷當前時間與有效時間
                            if (Integer.parseInt(dateString)<Integer.parseInt(strStartDate) || Integer.parseInt(dateString)>Integer.parseInt(strEndDate))
                            {
                                //清空圖像資訊
                                bitmap = null;
                                photoImage.setImageBitmap(bitmap);
                                setResultText(result = "票劵錯誤\r\n(逾期)無效票卡");
                                //labChang(false);
                                //lab5.Text = "票劵錯誤\r\n(逾期)無效票卡";
                                //lab6.Text = "";
                                //lab7.Text = "";
                                //txtQR.Focus();
                                //txtQR.SelectAll();
                            }
                            else//向中央取得權限
                            {
                                if (UpdateCertiCardPassRecord(tagNo, strCardID, xmlHelper.ReadValue("NameCode")) == true)
                                {
                                    //顯示登入者圖像
                                    if(bitmap!=null)
                                    {
                                        photoImage.setVisibility(View.VISIBLE);
                                        photoImage.setImageBitmap(bitmap);
                                    }

                                    ResultTxt3.setText("票券狀態    驗票成功\n\n票券身分    "+strCardName+"\n\n票券入場紀錄\n\n"+getDateTime());
                                }
                                else
                                {
                                    //清空圖像資訊
                                    bitmap = null;
                                    photoImage.setImageBitmap(bitmap);
                                    photoImage.setVisibility(View.GONE);

                                    ResultTxt3.setText("票券狀態    "+reMsg+"\n\n票券入場紀錄\n\n"+getDateTime());
                                }
                                RFData = "";
                            }
                        }
                        catch (Exception ex)
                        {
                            //清空圖像資訊
                            bitmap = null;
                            photoImage.setImageBitmap(bitmap);

                            ResultTxt3.setText("票券狀態    票劵錯誤\r\n(異常)請重新確認");
                        }
                    }
                } else {
                    //清空圖像資訊
                    bitmap = null;
                    photoImage.setImageBitmap(bitmap);
                    if (ary.length == 17) {
                        if (UpdatePassRecord(tagNo, false, ary) == true) {
                            setResultText(result = "參觀證"+reMsg);
                            //labChang(true);
                            //lab5.Text = reMsg;
                            //lab6.Text = "參觀證";

                            //lab8.Text = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                            //{
                            //    lab7.Text = GetBadgeType(ary[2].ToString());
                            //}
                        } else {
                            setResultText(result = "參觀證"+reMsg);
                            //labChang(false);
                            //lab5.Text = reMsg;
                            //lab6.Text = "";
                            //lab7.Text = "";
                            //lab8.Text = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
                        }
                        RFData = "";
                    }else {
                        //清空圖像資訊
                        bitmap = null;
                        photoImage.setImageBitmap(bitmap);

                        setResultText(result = "參觀證"+reMsg);
                        //labChang(false);
                        //lab5.Text = "票劵錯誤\r\n(格式)無效票卡";
                        //lab6.Text = "";
                        //lab7.Text = "";
                    }
                }
            } else{ // Authentication failed - Handle it

            }
        } catch (IOException ex) {
            WriteLog.appendLog("OnlineTicket.java/DownloadDeviceType/Exception:" + ex.toString());
        }
    }

    //一維條碼(BARCODE)驗票
    private void BarCodeCheck(String qr) {
        if (UpdateImportPass("", true, qr) == true)
        {
            setResultText(result = "參觀證"+reMsg);
            //labChang(true);
            //lab5.Text = reMsg;
            //lab6.Text = "參觀證";
            //lab7.Text = strUserTicketName;
            //lab8.Text = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
        }
        else
        {
            setResultText(result = "參觀證"+reMsg);
            //labChang(false);
            //lab5.Text = reMsg;
            //lab6.Text = "參觀證";
            //lab7.Text = strUserTicketName;
            //lab8.Text = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
        }
    }

    //二維條碼(QRCODE)驗票
    private void tickCheck() {
        if (UpdatePassRecord(ary[0].toString().trim(), true, ary) == true)
        {
            //labChang(true);
            //lab5.Text = reMsg;
            //lab6.Text = "參觀證";
            //lab8.Text = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");

            //{
            //    lab7.Text = GetBadgeType(ary[2].ToString());
            //}
            setResultText(result = "參觀證");
        }
        else
        {
            //labChang(false);
            //lab5.Text = reMsg;
            //lab6.Text = "";
            //lab7.Text = "";
            //lab8.Text = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
            setResultText(result = reMsg);
        }
    }

    //RFID驗票SP
    private Boolean UpdateCertiCardPassRecord(String guid, String cardNo, String ELCode) {
        Connection conUCCPR = DBCDPSConnection();
        byte[] fileBytes=null;
        try
        {
            String RF = guid.toUpperCase();
            CallableStatement cstmtUCCPRRRR = conUCCPR.prepareCall("{ call dbo.SP_UpdateCertiCardPassRecord(?,?,?,?,?,?)}");
            cstmtUCCPRRRR.setString("DeviceID",xmlHelper.ReadValue("MachineID"));
            cstmtUCCPRRRR.setString("DirectionType",InRBtn.isChecked() ? "I":"O");
            cstmtUCCPRRRR.setString("SensorCode",RF);
            cstmtUCCPRRRR.setString("CardNo",cardNo);
            cstmtUCCPRRRR.setString("EL_Code",ELCode);
            cstmtUCCPRRRR.registerOutParameter("ReturnMsg",java.sql.Types.VARCHAR);

            ResultSet rsUCCPRRRR = cstmtUCCPRRRR.executeQuery();

            while(rsUCCPRRRR.next())
            {
                fileBytes = rsUCCPRRRR.getBytes("Photofile");
            }

            bitmap=BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.length);

            String ReturnMsg="";
            if (!cstmtUCCPRRRR.getMoreResults()) {//此行判断是否还有更多的结果集,如果没有,接下来会处理output返回参数了
                ReturnMsg=cstmtUCCPRRRR.getString(6).trim();
            }
            if (ReturnMsg!=null)
            {
                reMsg = ReturnMsg;
                return reMsg.contains("成功");
            }
            else
            {
                reMsg = "驗票失敗\r\n無法取得回傳訊息";
                return false;
            }
        }
        catch (Exception ex)
        {
            reMsg = "驗票失敗\r\n發生無法預期錯誤";
            return false;
        }
        finally
        {
            try
            {
                conUCCPR.close();
            }
            catch (Exception ex)
            {

            }
        }
    }

    //一維條碼(BARCODE)驗票SP
    private Boolean UpdateImportPass(String guid, Boolean type, String barcode) {
        Connection conUIP = DBExhibitConnection();
        strUserTicketName = "";
        try
        {
            String RF = "";

            if (guid != "")
            {
                if (!type)//為true時候代表刷讀票劵，false為RFID
                {
                    String[] guidary = guid.split("-");
                    for (String guidstr : guidary)
                    {
                        RF += guidstr;
                    }
                }
            }
            CallableStatement cstmtUIP = conUIP.prepareCall("{ call dbo.SP_UpdateImportPass(?)}");
            cstmtUIP.setString("DeviceID",xmlHelper.ReadValue("MachineID"));
            cstmtUIP.setString("DirectionType",InRBtn.isChecked() ? "I":"O");
            cstmtUIP.setString("SensorCode",RF);
            cstmtUIP.setString("BarCode",barcode);
            cstmtUIP.registerOutParameter("ReturnMsg",java.sql.Types.VARCHAR);

            ResultSet rsUIP = cstmtUIP.executeQuery();

            while(rsUIP.next())
            {
                ResultSetMetaData rsMetaData = rsUIP.getMetaData();
                int numberOfColumns = rsMetaData.getColumnCount();

                for (int i = 1; i < numberOfColumns + 1; i++)
                {
                    String columnName = rsMetaData.getColumnName(i);

                    if ("BT_TypeName".equals(columnName))
                    {
                        if (rsUIP.getString("BT_TypeName")!= null)
                        {
                            strUserTicketName = rsUIP.getString("BT_TypeName");
                        }
                    }
                }
            }

            if (cstmtUIP.getString(5) != null)
            {
                reMsg = cstmtUIP.getString(5).trim();
                return reMsg.contains("成功");
            }
            else
            {
                reMsg = "驗票失敗\r\n無法取得回傳訊息";
                return false;
            }
        }
        catch (Exception ex)
        {
            reMsg = "驗票失敗\r\n發生無法預期錯誤";
            return false;
        }
        finally
        {
            try
            {
                conUIP.close();
            }
            catch (Exception ex)
            {

            }
        }
    }

    //二維條碼(QRCODE)驗票SP
    private Boolean UpdatePassRecord(String guid, Boolean type, String[] QRarray) {
        Connection conUPR = DBExhibitConnection();
        try
        {
            UUID u = UUID.randomUUID();
            u = UUID.nameUUIDFromBytes(QRarray[0].getBytes());

            String RF = "";

            if (type == false)//為true時候代表刷讀票劵，false為RFID
            {
                RF=guid.toUpperCase();
                //String[] guidary = guid.split("-");
                //for (String guidstr : guidary)
                //{
                //    RF += guidstr;
                //}
            }

            CallableStatement cstmtUPR = conUPR.prepareCall("{ call dbo.SP_UpdatePassRecord(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
            cstmtUPR.setString("DeviceID",xmlHelper.ReadValue("MachineID"));
            cstmtUPR.setString("DirectionType",InRBtn.isChecked() ? "I":"O");
            cstmtUPR.setString("SensorCode",RF);
            cstmtUPR.setString("SysCode",u.toString());
            //cstmtUPR.setString("EL_Code",QRarray[1].toString().trim());
            cstmtUPR.setString("EL_Code","TE2017");
            cstmtUPR.setString("BT_TypeID",QRarray[2].toString().trim());
            cstmtUPR.setString("VP_ValidDateRule",QRarray[3].toString().trim());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            if (QRarray[4].toString().trim() != "")
            {
                Date NewDateB =sdf.parse(QRarray[4].toString().trim());
                cstmtUPR.setDate("VP_ValidDateBegin",new java.sql.Date(NewDateB.getTime()));
            }
            else
            {
                cstmtUPR.setDate("VP_ValidDateBegin",null);
            }
            if (QRarray[5].toString().trim() != "")
            {
                Date NewDateE =sdf.parse(QRarray[5].toString().trim());
                cstmtUPR.setDate("VP_ValidDateEnd",new java.sql.Date(NewDateE.getTime()));
            }
            else
            {
                cstmtUPR.setDate("VP_ValidDateEnd",null);
            }
            cstmtUPR.setString("VP_ValidTimeRule",QRarray[6].toString().trim());
            //cstmtUPR.setString("VP_ValidTimeBegin",QRarray[7].toString().trim());
            cstmtUPR.setString("VP_ValidTimeBegin","20180815");
            //cstmtUPR.setString("VP_ValidTimeEnd",QRarray[8].toString().trim());
            cstmtUPR.setString("VP_ValidTimeEnd","20180820");
            cstmtUPR.setString("VP_UseAreaAssign",QRarray[9].toString().trim());
            //cstmtUPR.setString("VP_UsageTimeType",QRarray[10].toString().trim());
            cstmtUPR.setString("VP_UsageTimeType","0");
            cstmtUPR.setString("VP_UsageTimeTotal",QRarray[11].toString().trim());
            cstmtUPR.setString("VP_UsageTimePerDay",QRarray[12].toString().trim());
            cstmtUPR.setString("IV_CheckCode",QRarray[15].toString().trim());
            cstmtUPR.setString("IV_CheckCode2",QRarray[16].toString().trim());
            cstmtUPR.setString("IsFuncCar","N");
            cstmtUPR.registerOutParameter("ReturnMsg",java.sql.Types.VARCHAR);
            cstmtUPR.execute();
            if (cstmtUPR.getString(20) != null)
            {
                reMsg = cstmtUPR.getString(20).trim();
                return reMsg.contains("成功");
            }
            else
            {
                reMsg = "驗票失敗\r\n無法取得回傳訊息";
                return false;
            }
        }
        catch (Exception ex)
        {
            reMsg = "驗票失敗\r\n發生無法預期錯誤";
            return false;
        }
        finally
        {
            try
            {
                conUPR.close();
            }
            catch (Exception ex)
            {

            }
        }
    }

    //中央下載資料SP
    public ResultSet DownloadDeviceSetup(){
        ResultSet rsDDS=null;
        Connection conDDS = DBCDPSConnection();
        try
        {
            CallableStatement cstmtDDS = conDDS.prepareCall("{ call dbo.SP_DownloadDeviceSetup(?)}");
            cstmtDDS.setString("DeviceID",xmlHelper.ReadValue("MachineID"));
            rsDDS = cstmtDDS.executeQuery();
        }
        catch (Exception e)
        {
            WriteLog.appendLog("OnlineTickets.java/DownloadDeviceSetup/Exception:" + e.toString());
        }
        return rsDDS;
    }

    //中央下載資料SP
    private String DownloadDeviceType() {
        Connection conDDT = DBCDPSConnection();
        String value="1";
        try
        {
            CallableStatement cstmtDDT = conDDT.prepareCall("{ call dbo.SP_DownloadExhibitionSetup(?)}");
            cstmtDDT.setString("DEVICE_ID",xmlHelper.ReadValue("MachineID"));
            ResultSet rsDDT = cstmtDDT.executeQuery();
            while (rsDDT.next())
            {
                if(rsDDT.getString("ES_ParamCode").equals("UDSTime"))
                {
                    UDSTime = Integer.parseInt(rsDDT.getString("ES_ParamValue"));
                }

                if (rsDDT.getString("ES_ParamCode").equals("IDCF"))
                {
                    value = rsDDT.getString("ES_ParamValue");
                }
            }
            return value;
        }
        catch (Exception e)
        {
            WriteLog.appendLog("OnlineTicket.java/DownloadDeviceType/Exception:" + e.toString());
            return "";
        }
        finally
        {
            try
            {
                conDDT.close();
            }
            catch (Exception ex)
            {

            }
        }
    }

    //取得展覽名稱、IP、帳號、密碼SP
    private ResultSet DownloadAllExhibition(String EL) {
        ResultSet rsDAE=null;
        Connection conDAE = DBCDPSConnection();
        try
        {
            //CallableStatement cstmt = conDownloadAllExhibition.prepareCall("{ call dbo.SP_DownloadExhibitionInfo(?)}");
            //cstmt.setString("EL_Code",EL);
            String query = "select E.EL_Code,convert(nvarchar,EL_Name) as EL_Name,EL_NameEng, EL_ShowType,EL_InitStartDate,EL_InitUntilDate,EL_ExhibitStartDate,EL_ExhibitUntilDate,EL_FinishStartDate,EL_FinishUntilDate,EL_Organizers,EL_Status,EL_DB_IP,EL_DB_Account,EL_DB_PWD " +
                           "from cExhibitionList E "+
                           "where E.EL_Code = '"+EL+"'";
            Statement stmtDAE = conDAE.createStatement();
            rsDAE = stmtDAE.executeQuery(query);
        }
        catch (Exception e)
        {
            WriteLog.appendLog("OnlineTicket.java/DownloadAllExhibition/Exception:" + e.toString());
        }
        return rsDAE;
    }

    //下載票劵型態別SP
    private ResultSet DownloadBadgeType() {
        ResultSet rsDBT=null;
        Connection conDBT = DBExhibitConnection();
        try
        {
            //CallableStatement cstmt = conDownloadAllExhibition.prepareCall("{ call dbo.SP_DownloadBadgeType(?)}");
            //cstmt.setString("DEVICE_ID",xmlHelper.ReadValue("MachineID"));
            String query = "select BT_TypeID,convert(nvarchar,BT_TypeName)as BT_TypeName,BT_MaterialType,convert(nvarchar,BT_Memo) as BT_Memo from eBadgeType";
            Statement stmtDBT = conDBT.createStatement();
            rsDBT = stmtDBT.executeQuery(query);
        }
        catch (Exception e)
        {
            WriteLog.appendLog("OnlineTicket.java/DownloadBadgeType/Exception:" + e.toString());
        }
        return rsDBT;
    }

    //取得AES256的密碼SP
    private String[] DownloadExhibitionSetup() {
        Connection conDES = DBExhibitConnection();
        ResultSet rsDES=null;
        String[] strQRPWD = { "", "" };
        try
        {
            CallableStatement cstmtDES = conDES.prepareCall("{ call dbo.SP_DownloadExhibitionSetup(?)}");
            cstmtDES.setString("Device_ID",xmlHelper.ReadValue("MachineID"));
            rsDES = cstmtDES.executeQuery();

            while (rsDES.next())
            {
                if (rsDES.getString("ES_ParamCode").equals("QRPWD"))
                {
                    strQRPWD[0] = rsDES.getString("ES_ParamValue");
                    strQRPWD[1] = rsDES.getString("ES_ParamValue2");
                    return strQRPWD;
                }
            }
            return null;
        }
        catch (Exception e)
        {
            WriteLog.appendLog("OnlineTicket.java/DownloadExhibitionSetup/Exception:" + e.toString());
            return null;
        }
    }

    /// 主動回覆當前狀態SP
    private Boolean UpdateDeviceStatus() {
        Connection conUDS = DBExhibitConnection();
        String reportMessage = "";
        try
        {
            CallableStatement cstmtUDS = conUDS.prepareCall("{ call dbo.SP_UpdateDeviceStatus(?,?,?)}");
            cstmtUDS.setString("DeviceID",xmlHelper.ReadValue("MachineID"));
            cstmtUDS.setString("ServiceType","0001");
            cstmtUDS.registerOutParameter("ReturnMsg",java.sql.Types.VARCHAR);
            cstmtUDS.execute();

            if (cstmtUDS.getString(3) != null)
            {
                reportMessage = cstmtUDS.getString(3).trim();
                return reportMessage.contains("成功");
            }
            else
            {
                return false;
            }
        }
        catch (Exception e)
        {
            WriteLog.appendLog("OnlineTicket.java/UpdateDeviceStatus/Exception:" + e.toString());
            return false;
        }
    }

    //定時主動回報狀態
    private Runnable updateTimer = new Runnable() {
        public void run()
        {
            UpdateDeviceStatus();
            handler.postDelayed(this, mainTimerInterval);
        }
    };

    // 解碼作業
    private String QRDecod(String _PacketData, String iv) {
        try
        {
            String newKey = "",strDecod = _PacketData;

            for (String strKey : sQRPWDItem)
            {
                newKey = MakeKeyLen32(strKey);
                byte[] descryptBytes = DecryptAES256(newKey.getBytes("UTF-8"),iv.getBytes("UTF-8"),Base64.decode(_PacketData, Base64.DEFAULT));
                String descryptResult = new String(descryptBytes);
                if (descryptResult.split("@").length == 17)
                {
                    strDecod = descryptResult;
                    break;
                }
            }
            return strDecod;
        }
        catch (Exception e)
        {
            WriteLog.appendLog("OnlineTicket.java/QRDecod/Exception:" + e.toString());
            return _PacketData;
        }
    }

    //QRCODE解碼
    public static byte[] DecryptAES256 (byte[] keyBytes,byte[] ivBytes,byte[] valueBytes) {
        try
        {
            AlgorithmParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            SecretKeySpec newKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, newKey,ivSpec);
            return cipher.doFinal(valueBytes);
        }
        catch (Exception e)
        {
            WriteLog.appendLog("OnlineTicket.java/DecryptAES256/Exception:" + e.toString());
            return null;
        }
    }

    public static String byte2hex(byte[] b) { // 二进制转字符串
        String hs = "";
        String stmp = "";
        for (int n = 0; n < b.length; n++) {
            if (b[n] ==(byte)0){
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
            strReturn = new String(byteData,"ISO8859-1");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return strReturn;
    }

    //Key長度補為32
    private String MakeKeyLen32(String sKey) {
        String Result = sKey;
        if (sKey.length() < 32)
        {
            Result = Result+String.format("%1$0"+(32-sKey.length())+"d",0);
        }
        return Result;
    }

    //設定票券狀態文字
    private void setResultText(String text) {
        ResultTxt.setText(text);
    }

    //設定票券狀態文字
    private void setResultText2(String text) {
        ResultTxt2.setText(text);
    }

    //震動
    public void setVibrate(int time){
        Vibrator myVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        myVibrator.vibrate(time);
    }

    //取得現在時間 yyyy-MM-dd HH:mm:ss
    public String getDateTime(){
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar c = Calendar.getInstance();
        String str = df.format(c.getTime());
        return str;
    }

    //監控網路狀態
    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = connectMgr.getActiveNetworkInfo();
            if (mNetworkInfo == null)
            {
                Toast.makeText(OnlineTickets.this, "連線中斷", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    };
}