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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Created by USER on 2015/12/15.
 */
public class ConnectSetting extends Activity {
    Button Confirm_btn,Return_btn;
    EditText MachineID_et,IP_et,SqlAccount_et,SqlPassword_et,Password_et,URL_et;
    CheckBox RFID_cb;
    File file;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.connect_setting);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.titlebar);

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
            Document dom;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {
                DocumentBuilder db = dbf.newDocumentBuilder();
                dom = db.parse(file);
                Element doc = dom.getDocumentElement();

                MachineID_et.setText(getTextValue("", doc, "MachineID"));
                IP_et.setText(getTextValue("", doc, "ServerIP"));
                SqlAccount_et.setText(getTextValue("", doc, "sa"));
                SqlPassword_et.setText(getTextValue("", doc, "SQLPassWord"));
                Password_et.setText(getTextValue("", doc, "SetupPassWord"));
                URL_et.setText(getTextValue("", doc, "WebServiceUrl"));
                RFID_cb.setChecked(getTextValue("", doc, "RFID") .equals("OPEN")  ? true : false);
            } catch (ParserConfigurationException pce) {
                System.out.println(pce.getMessage());
            } catch (SAXException se) {
                System.out.println(se.getMessage());
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
            }
        }

        Confirm_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                    try {
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
                        xmlSerializer.text("\n\t");
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

                Intent callSub = new Intent();
                callSub.setClass(ConnectSetting.this, MainActivity.class);
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

    private String getTextValue(String def, Element doc, String tag) {
        String value = def;
        NodeList nl;
        nl = doc.getElementsByTagName(tag);
        if (nl.getLength() > 0 && nl.item(0).hasChildNodes()) {
            value = nl.item(0).getFirstChild().getNodeValue();
        }
        return value;
    }
}
