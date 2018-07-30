package com.example.user.twtc_app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Xml;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;

/**
 * Created by USER on 2015/12/15.
 */
public class ConnectSetting extends Activity {
    Button Confirm_btn,Return_btn;
    EditText MachineID_et,IP_et,SqlAccount_et,SqlPassword_et,Password_et,URL_et;
    CheckBox RFID_cb;
    File file;
    XmlHelper xmlHelper;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.connect_setting);

        xmlHelper=new XmlHelper(getFilesDir()+"//connectData.xml");
        //getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.titlebar);

        Confirm_btn=(Button)findViewById(R.id.Confirm_btn);
        Return_btn=(Button)findViewById(R.id.Return_btn);
        MachineID_et=(EditText) findViewById(R.id.MachineID_et);
        IP_et=(EditText)findViewById(R.id.IP_et);
        SqlAccount_et=(EditText)findViewById(R.id.SqlAccount_et);
        SqlPassword_et=(EditText)findViewById(R.id.SqlPassword_et);
        Password_et=(EditText)findViewById(R.id.Password_et);
        URL_et=(EditText)findViewById(R.id.URL_et);
        RFID_cb=(CheckBox) findViewById(R.id.RFID_cb);

        file = new File(getFilesDir()+"//connectData.xml");

        if(file.exists()){
            MachineID_et.setText(xmlHelper.ReadValue("MachineID"));
            IP_et.setText(xmlHelper.ReadValue("ServerIP"));
            SqlAccount_et.setText(xmlHelper.ReadValue("sa"));
            SqlPassword_et.setText(xmlHelper.ReadValue("SQLPassWord"));
            Password_et.setText(xmlHelper.ReadValue("SetupPassWord"));
            URL_et.setText(xmlHelper.ReadValue("WebServiceUrl"));
            RFID_cb.setChecked(xmlHelper.ReadValue("RFID") .equals("OPEN")  ? true : false);
        }

        Confirm_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!file.exists()){
                    try {
                        FileOutputStream fos = new FileOutputStream(new File(getFilesDir(), "connectData.xml"));
                        XmlSerializer xmlSerializer = Xml.newSerializer();
                        StringWriter writer = new StringWriter();
                        xmlSerializer.setOutput(writer);
                        xmlSerializer.startDocument("UTF-8", true);
                        xmlSerializer.text("\n");
                        xmlSerializer.startTag(null, "Setup");
                        xmlSerializer.text("\n");
                        xmlSerializer.endTag(null, "Setup");
                        xmlSerializer.endDocument();
                        xmlSerializer.flush();
                        String dataWrite = writer.toString();
                        fos.write(dataWrite.getBytes());
                        fos.close();
                    }
                    catch (FileNotFoundException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    catch (IllegalArgumentException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    catch (IllegalStateException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                xmlHelper.WriteValue("MachineID",MachineID_et.getText().toString());
                xmlHelper.WriteValue("ServerIP",IP_et.getText().toString());
                xmlHelper.WriteValue("sa",SqlAccount_et.getText().toString());
                xmlHelper.WriteValue("SQLPassWord",SqlPassword_et.getText().toString());
                xmlHelper.WriteValue("SetupPassWord",Password_et.getText().toString());
                xmlHelper.WriteValue("WebServiceUrl",URL_et.getText().toString());
                xmlHelper.WriteValue("RFID",RFID_cb.isChecked() ? "OPEN":"CLOSE");
                /*try {
                    FileOutputStream fos = new FileOutputStream(new File(getFilesDir(), "connectData.xml"));
                    XmlSerializer xmlSerializer = Xml.newSerializer();
                    StringWriter writer = new StringWriter();
                    xmlSerializer.setOutput(writer);
                    xmlSerializer.startDocument("UTF-8", true);
                    xmlSerializer.text("\n");
                    xmlSerializer.startTag(null, "Setup");
                    xmlSerializer.text("\n\t");
                    xmlSerializer.startTag(null, "MachineID");
                    xmlSerializer.text(MachineID_et.getText().toString());
                    xmlSerializer.endTag(null, "MachineID");
                    xmlSerializer.text("\n\t");
                    xmlSerializer.startTag(null, "ServerIP");
                    xmlSerializer.text(IP_et.getText().toString());
                    xmlSerializer.endTag(null, "ServerIP");
                    xmlSerializer.text("\n\t");
                    xmlSerializer.startTag(null,"sa");
                    xmlSerializer.text(SqlAccount_et.getText().toString());
                    xmlSerializer.endTag(null, "sa");
                    xmlSerializer.text("\n\t");
                    xmlSerializer.startTag(null,"SQLPassWord");
                    xmlSerializer.text(SqlPassword_et.getText().toString());
                    xmlSerializer.endTag(null, "SQLPassWord");
                    xmlSerializer.text("\n\t");
                    xmlSerializer.startTag(null,"SetupPassWord");
                    xmlSerializer.text(Password_et.getText().toString());
                    xmlSerializer.endTag(null, "SetupPassWord");
                    xmlSerializer.text("\n\t");
                    xmlSerializer.startTag(null,"WebServiceUrl");
                    xmlSerializer.text(URL_et.getText().toString());
                    xmlSerializer.endTag(null, "WebServiceUrl");
                    xmlSerializer.text("\n\t");
                    xmlSerializer.startTag(null,"RFID");
                    xmlSerializer.text(RFID_cb.isChecked() ? "OPEN":"CLOSE");
                    xmlSerializer.endTag(null, "RFID");
                    xmlSerializer.text("\n");
                    xmlSerializer.endTag(null, "Setup");
                    xmlSerializer.endDocument();
                    xmlSerializer.flush();
                    String dataWrite = writer.toString();
                    fos.write(dataWrite.getBytes());
                    fos.close();
                }
                catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                catch (IllegalArgumentException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                catch (IllegalStateException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }*/

                Intent callSub = new Intent();
                callSub.setClass(ConnectSetting.this, AfterLogin.class);
                startActivityForResult(callSub, 0);
            }
        });

        Return_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
