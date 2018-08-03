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
import android.media.Image;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
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

import java.security.spec.AlgorithmParameterSpec;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by USER on 2015/11/19.
 */
public class OnlineTickets extends Activity {
    private static final String key="SET31275691$00000000000000000000";
    TextView ResultTxt,ResultTxt2;
    String result="",DEVICE_ID,SPS_ID,strUserTicketName,reMsg;;
    String[] ary;
    Button ReturnBtn,HomeBtn;
    RadioButton InRBtn,OutRBtn;
    LinearLayout FailedLayout;
    private Object m_LockFlag = new Object();
    //SQLITE
    private MyDBHelper mydbHelper;

    //剪貼簿
    private ClipboardManager cbMgr;
    private ClipboardManager.OnPrimaryClipChangedListener mPrimaryClipChangedListener;

    //RFID
    NfcAdapter mNfcAdapter;
    PendingIntent mPendingIntent;
    Bitmap bitmap;
    private Handler mThreadHandler;
    private HandlerThread mThread;
    private class RFIDBlock4Info {
        public Byte bCardType;
        public Byte[] bStartDate;
        public Byte[] bEndDate;
        public Byte[] bCardID;
        public RFIDBlock4Info(Byte[] a, Byte[] b, Byte[] c) {
            bCardType = 0;
            bStartDate = a;
            bEndDate = b;
            bCardID = c;
        }
    }
    RFIDBlock4Info BlockInfo = new RFIDBlock4Info(new Byte[3], new Byte[3], new Byte[8]);

    //參數XML
    XmlHelper xmlHelper;
    private int UDSTime = 30;

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

        xmlHelper=new XmlHelper(getFilesDir()+"//connectData.xml");

        ResultTxt=(TextView) findViewById(R.id.ResultTxt);
        ReturnBtn=(Button)findViewById(R.id.ReturnBtn);
        HomeBtn=(Button)findViewById(R.id.HomeBtn);
        InRBtn=(RadioButton) findViewById(R.id.InRBtn);
        OutRBtn=(RadioButton)findViewById(R.id.OutRBtn);
        ResultTxt2=(TextView) findViewById(R.id.ResultTxt2);
        FailedLayout=(LinearLayout) findViewById(R.id.FailedLayout);

        mydbHelper = new MyDBHelper(this);

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
        InRBtn.setChecked(true);

        //掃描驗票
        cbMgr=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        mPrimaryClipChangedListener =new ClipboardManager.OnPrimaryClipChangedListener(){
            public void onPrimaryClipChanged() {
                try{
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

        //註冊網路狀態監聽
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);

        //RFID
        if (xmlHelper.ReadValue("RFID").equals("OPEN"))
        {
            mThread = new HandlerThread("RFID");
            mThread.start();
            mThreadHandler=new Handler(mThread.getLooper());
            mThreadHandler.post(rfid);
        }
    }//End ON CREATE

    @Override
    protected void onPause() {
        super.onPause();
        cbMgr.removePrimaryClipChangedListener(mPrimaryClipChangedListener);
        if (mThreadHandler != null) {
            mThreadHandler.removeCallbacks(rfid);
        }
        if (mThread != null) {
            mThread.quit();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(connectionReceiver);
    }

    private Runnable rfid=new Runnable () {
        public void run() {
            int iRet, iReLoadTime = 0;
            byte[] pATQ = new byte[2];
            byte[] pSAK = new byte[1];
            byte[] pUid = new byte[7];
            byte[] pUidLen = new byte[1];
            byte[] pData = new byte[16];
            byte[] tempUid = null;

            mNfcAdapter = NfcAdapter.getDefaultAdapter(OnlineTickets.this);
            if (mNfcAdapter == null) {
                Toast.makeText(OnlineTickets.this, "開啟RFID失敗", Toast.LENGTH_SHORT).show();
                return;
            }
            mPendingIntent = PendingIntent.getActivity(OnlineTickets.this, 0, new Intent(OnlineTickets.this,
                    getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

            int i , iBlockCount = 0;
            int dwMode;
            //button1.Enabled = false;
            String L13 = "";
            String rfKey = "FFFFFFFFFFFF";
            String RFData = "";
            String sIOStatus = "";

            String strCardID = "" , strStartDate = "" , strEndDate = "" , strCardName = "";
            DateTime dtStartDate , dtEndDate;
            Image img = null;

            /*for (; ; ) {
                synchronized(m_LockFlag){
                    BlockInfo.bCardType = 0x00;
                    BlockInfo.bCardID=new Byte[BlockInfo.bCardID.length];
                    BlockInfo.bEndDate=new Byte[BlockInfo.bEndDate.length];
                    BlockInfo.bStartDate=new Byte[BlockInfo.bStartDate.length];
                    if (ary != null) ary=new String[ary.length];

                    {
                        strFirstRFID = ByteArrayToString(pUid);//取得目前UID

                        if (strFirstRFID == "")//判斷第二次是否為空
                        {
                            strSecondRFID = strFirstRFID;//帶入第一次讀取到UID
                            iReLoadTime = 0;
                            sIOStatus = strIOStatus;
                        }
                        else
                        {
                            //兩次均相同 (包含相同作業)
                            if (strFirstRFID == strSecondRFID && (sIOStatus == strIOStatus))
                            {
                                iReLoadTime++;
                                if (iReLoadTime > 10)
                                {
                                    iReLoadTime = 0;
                                }
                                else
                                {
                                    //顯示圖像
                                    Thread.Sleep(500);

                                    this.BeginInvoke((ThreadStart)delegate
                                    {
                                        this.picUserPhoto.Image = img;
                                    });

                                    iRet = NFCMifareLibNet.Api.NFCMifareRadioOff();

                                    if (iRet != NFCMifareLibNet.Def.NFC_OK)
                                    {
                                        continue;
                                    }
                                }
                            }
                            else
                            {
                                //帶入第一次讀取到UID
                                strSecondRFID = strFirstRFID;
                                iReLoadTime = 0;
                                sIOStatus = strIOStatus;
                                //清空顯示圖像
                                this.BeginInvoke((ThreadStart)delegate
                                {
                                    this.picUserPhoto.Image = null;
                                });
                            }
                        }
                    }
                }
            }*/
        }
    };

    @Override
    protected void onNewIntent(Intent intent){
        getTagInfo(intent);
    }

    private void getTagInfo(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        String tagNo="";
        byte[] tagId = tag.getId();
        for(int i=0; i<tagId.length; i++){
            if(Integer.toHexString(tagId[i] & 0xFF).length()<2){
                tagNo +="0";
            }
            tagNo += Integer.toHexString(tagId[i] & 0xFF);
        }
    }

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

    private void tickCheck()
    {
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

    //驗票
    private Boolean UpdatePassRecord(String guid, Boolean type, String[] QRarray)
    {
        Connection conUPR = DBExhibitConnection();
        try
        {
            UUID u = UUID.randomUUID();
            u = UUID.nameUUIDFromBytes(QRarray[0].getBytes());

            String RF = "";

            if (type == false)//為true時候代表刷讀票劵，false為RFID
            {
                String[] guidary = guid.split("-");
                for (String guidstr : guidary)
                {
                    RF += guidstr;
                }
            }

            CallableStatement cstmtUPR = conUPR.prepareCall("{ call dbo.SP_UpdatePassRecord(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}");
            cstmtUPR.setString("DeviceID",xmlHelper.ReadValue("MachineID"));
            cstmtUPR.setString("DirectionType",InRBtn.isChecked() ? "I":"O");
            cstmtUPR.setString("SensorCode",RF);
            cstmtUPR.setString("SysCode",u.toString());
            cstmtUPR.setString("EL_Code","TE2017");///////////////////////////////////
            cstmtUPR.setString("BT_TypeID",QRarray[2].toString().trim());
            cstmtUPR.setString("VP_ValidDateRule",QRarray[3].toString().trim());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            if (QRarray[4].toString().trim() != "") {
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
            cstmtUPR.setString("VP_ValidTimeBegin",QRarray[7].toString().trim());
            cstmtUPR.setString("VP_ValidTimeEnd",QRarray[8].toString().trim());
            cstmtUPR.setString("VP_UseAreaAssign",QRarray[9].toString().trim());
            cstmtUPR.setString("VP_UsageTimeType","0");///////////////////////////////////
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
            try{
                conUPR.close();
            }catch (Exception ex) {

            }
        }
    }

    //驗票
    private Boolean UpdateImportPass(String guid, Boolean type, String barcode)
    {
        Connection conUIP = DBExhibitConnection();
        strUserTicketName = "";
        try {
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

            while(rsUIP.next()){
                ResultSetMetaData rsMetaData = rsUIP.getMetaData();
                int numberOfColumns = rsMetaData.getColumnCount();

                for (int i = 1; i < numberOfColumns + 1; i++) {
                    String columnName = rsMetaData.getColumnName(i);

                    if ("BT_TypeName".equals(columnName)) {
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
        catch (Exception ex) {
            reMsg = "驗票失敗\r\n發生無法預期錯誤";
            return false;
        }
        finally {
            try{
                conUIP.close();
            }catch (Exception ex) {

            }
        }
    }

    public ResultSet DownloadDeviceSetup(){
        ResultSet rsDDS=null;
        Connection conDDS = DBCDPSConnection();
        try {
            CallableStatement cstmtDDS = conDDS.prepareCall("{ call dbo.SP_DownloadDeviceSetup(?)}");
            cstmtDDS.setString("DeviceID",xmlHelper.ReadValue("MachineID"));
            rsDDS = cstmtDDS.executeQuery();
        }
        catch (Exception e) {
            WriteLog.appendLog("OnlineTickets.java/DownloadDeviceSetup/Exception:" + e.toString());
        }
        return rsDDS;
    }

    private String DownloadDeviceType() {
        Connection conDDT = DBCDPSConnection();
        String value="1";
        try {
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
    }

    //取得展覽名稱、IP、帳號、密碼
    private ResultSet DownloadAllExhibition(String EL)
    {
        ResultSet rsDAE=null;
        Connection conDAE = DBCDPSConnection();
        try {
            //CallableStatement cstmt = conDownloadAllExhibition.prepareCall("{ call dbo.SP_DownloadExhibitionInfo(?)}");
            //cstmt.setString("EL_Code",EL);
            String query = "select E.EL_Code,convert(nvarchar,EL_Name) as EL_Name,EL_NameEng, EL_ShowType,EL_InitStartDate,EL_InitUntilDate,EL_ExhibitStartDate,EL_ExhibitUntilDate,EL_FinishStartDate,EL_FinishUntilDate,EL_Organizers,EL_Status,EL_DB_IP,EL_DB_Account,EL_DB_PWD " +
                           "from cExhibitionList E "+
                           "where E.EL_Code = '"+EL+"'";
            Statement stmtDAE = conDAE.createStatement();
            rsDAE = stmtDAE.executeQuery(query);
        }
        catch (Exception e) {
            WriteLog.appendLog("OnlineTicket.java/DownloadAllExhibition/Exception:" + e.toString());
        }
        return rsDAE;
    }

    //下載票劵型態別
    private ResultSet DownloadBadgeType()
    {
        ResultSet rsDBT=null;
        Connection conDBT = DBExhibitConnection();
        try {
            //CallableStatement cstmt = conDownloadAllExhibition.prepareCall("{ call dbo.SP_DownloadBadgeType(?)}");
            //cstmt.setString("DEVICE_ID",xmlHelper.ReadValue("MachineID"));
            String query = "select BT_TypeID,convert(nvarchar,BT_TypeName)as BT_TypeName,BT_MaterialType,convert(nvarchar,BT_Memo) as BT_Memo from eBadgeType";
            Statement stmtDBT = conDBT.createStatement();
            rsDBT = stmtDBT.executeQuery(query);
        }
        catch (Exception e) {
            WriteLog.appendLog("OnlineTicket.java/DownloadBadgeType/Exception:" + e.toString());
        }
        return rsDBT;
    }

    //取得AES256的密碼
    private String[] DownloadExhibitionSetup()
    {
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

    /// 主動回覆當前狀態
    private Boolean UpdateDeviceStatus()
    {
        Connection conUDS = DBExhibitConnection();
        String reportMessage = "";
        try {
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
        }catch (Exception e) {
            WriteLog.appendLog("OnlineTicket.java/UpdateDeviceStatus/Exception:" + e.toString());
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

    /// 解碼作業
    private String QRDecod(String _PacketData, String iv) {
        try {
            String newKey = "",strDecod = _PacketData;

            for (String strKey : sQRPWDItem) {
                newKey = MakeKeyLen32(strKey);
                byte[] descryptBytes = DecryptAES256(newKey.getBytes("UTF-8"),iv.getBytes("UTF-8"),Base64.decode(_PacketData, Base64.DEFAULT));
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
    public static byte[] DecryptAES256 (byte[] keyBytes,byte[] ivBytes,byte[] valueBytes) {
        try {
            AlgorithmParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            SecretKeySpec newKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, newKey,ivSpec);
            return cipher.doFinal(valueBytes);
        } catch (Exception e) {
            WriteLog.appendLog("OnlineTicket.java/DecryptAES256/Exception:" + e.toString());
            return null;
        }
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

    //監控網路狀態
    private final BroadcastReceiver connectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = connectMgr.getActiveNetworkInfo();
            if (mNetworkInfo == null) {
                Toast.makeText(OnlineTickets.this, "連線中斷", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    };
}