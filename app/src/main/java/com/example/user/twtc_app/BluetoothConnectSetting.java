package com.example.user.twtc_app;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Created by mds_userz on 2018/3/27.
 */

public class BluetoothConnectSetting extends Activity
{
    public static List<BluetoothDevice> connectedBluetoothDevices=new ArrayList<BluetoothDevice>();
    public static BluetoothDevice connectedBluetoothDevice;
    public static BluetoothSocket mmSocket;
    private ClipboardManager cbMgr;
    private ClipboardManager.OnPrimaryClipChangedListener mPrimaryClipChangedListener;
    EditText Address;
    Button BtBtn,ConnectBtn,ReturnBtn,HomeBtn;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mmDevice;
    String address;
    XmlHelper xmlHelper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.bluetooth_connect_setting);

        BtBtn=(Button)findViewById(R.id.BtBtn);
        ConnectBtn = (Button) findViewById(R.id.ConnectBtn);
        ReturnBtn=(Button)findViewById(R.id.ReturnBtn);
        HomeBtn=(Button)findViewById(R.id.HomeBtn);
        Address=(EditText) findViewById(R.id.Address);

        xmlHelper=new XmlHelper(getFilesDir()+"//connectData.xml");

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mReceiver, filter);

        IntentFilter filter2 = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver2, filter2);

        if(mBluetoothAdapter.isEnabled()) {
            BtBtn.setBackgroundResource(R.drawable.bton);
        }else{
            BtBtn.setBackgroundResource(R.drawable.btoff);
        }

        //Bluetooth OnOff
        BtBtn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                BtBtn.setClickable(false);
                if (mBluetoothAdapter.isEnabled()) {
                    BtBtn.setBackgroundResource(R.drawable.btoff);
                    mBluetoothAdapter.disable();
                } else {
                    BtBtn.setBackgroundResource(R.drawable.bton);
                    mBluetoothAdapter.enable();
                }
            }
        });

        //Bluetooth Connect Button
        ConnectBtn.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                try{
                    if(connectedBluetoothDevices.size()>0){
                        Toast.makeText(BluetoothConnectSetting.this,"藍牙裝置已連線", Toast.LENGTH_SHORT).show();
                    }else{
                        findBT(Address.getText().toString());
                        Toast.makeText(BluetoothConnectSetting.this,"藍牙裝置連線成功", Toast.LENGTH_SHORT).show();
                    }
                    xmlHelper.WriteValue("BlueToothID",Address.getText().toString().trim());
                    Intent intent = new Intent();
                    intent.setClass(BluetoothConnectSetting.this, BluetoothTickets.class);
                    startActivity(intent);
                    BluetoothConnectSetting.this.finish();
                    //Intent callSub = new Intent();
                    //callSub.setClass(BluetoothConnectSetting.this, BluetoothTickets.class);
                    //startActivityForResult(callSub, 0);
                }catch(Exception ex){
                    Toast.makeText(BluetoothConnectSetting.this, "連接藍牙失敗", Toast.LENGTH_SHORT).show();
                }
            }
        });

        this.cbMgr=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        mPrimaryClipChangedListener =new ClipboardManager.OnPrimaryClipChangedListener(){
            public void onPrimaryClipChanged() {
                try{
                    setVibrate(100);
                    Address.setText("");
                    if(connectedBluetoothDevices.size()>0){
                        Toast.makeText(BluetoothConnectSetting.this,"藍牙裝置已連線", Toast.LENGTH_SHORT).show();
                    }else{
                        address=cbMgr.getPrimaryClip().getItemAt(0).getText().toString();
                        findBT(address);
                        Toast.makeText(BluetoothConnectSetting.this,"藍牙裝置連線成功", Toast.LENGTH_SHORT).show();
                    }
                    xmlHelper.WriteValue("BlueToothID",address.trim());
                    Intent intent = new Intent();
                    intent.setClass(BluetoothConnectSetting.this, BluetoothTickets.class);
                    startActivity(intent);
                    BluetoothConnectSetting.this.finish();
                    //Intent callSub = new Intent();
                    //callSub.setClass(BluetoothConnectSetting.this, BluetoothTickets.class);
                    //startActivityForResult(callSub, 0);
                }catch(Exception ex){
                    Toast.makeText(BluetoothConnectSetting.this, "連接藍牙失敗", Toast.LENGTH_SHORT).show();
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

        HomeBtn.setOnClickListener(new View.OnClickListener(){
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        unregisterReceiver(mReceiver2);
    }

    void findBT(String Address)
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            Toast.makeText(BluetoothConnectSetting.this,"無藍牙功能", Toast.LENGTH_SHORT).show();
        }

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getAddress().equals(Address)){
                    mmDevice=device;
                    try{
                        connectBT();
                        connectedBluetoothDevice=mmDevice;
                        connectedBluetoothDevices.add(device);
                        //Toast.makeText(BluetoothConnectSetting.this, "藍牙連線成功", Toast.LENGTH_SHORT).show();
                    }catch (Exception e) {

                    }
                }
            }
        }
        if(connectedBluetoothDevices.size()==0){
            mBluetoothAdapter.startDiscovery();
        }
    }

    void connectBT() throws IOException
    {
        UUID uuid = UUID.fromString("7A51FDC2-FDDF-4c9b-AFFC-98BCD91BF93B"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
    }

    //震動
    public void setVibrate(int time){
        Vibrator myVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        myVibrator.vibrate(time);
    }

    //The BroadcastReceiver that listens for bluetooth broadcasts
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                if(device.getAddress().toString().equals(address)){
                    try {
                        Method method = device.getClass().getMethod("createBond", (Class[]) null);
                        method.invoke(device, (Object[]) null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                if (!connectedBluetoothDevices.contains(device)) {
                    connectedBluetoothDevices.add(device);
                }
            }else if (action.equals(device.ACTION_ACL_DISCONNECTED)) {
                if (connectedBluetoothDevices.contains(device)) {
                    connectedBluetoothDevices.remove(device);
                }
            }
        }
    };

    //藍牙狀態改變
    private final BroadcastReceiver mReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        BtBtn.setBackgroundResource(R.drawable.btoff);
                        BtBtn.setClickable(true);
                        connectedBluetoothDevices.clear();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        BtBtn.setClickable(false);
                        break;
                    case BluetoothAdapter.STATE_ON:
                        BtBtn.setBackgroundResource(R.drawable.bton);
                        BtBtn.setClickable(true);
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        BtBtn.setClickable(false);
                        break;
                }
            }
        }
    };
}
