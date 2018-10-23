package com.example.user.twtc_app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Xml;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;

/**
 * Created by Jeff.
 */
public class ConnectSetting extends Activity {
    Button Confirm_btn,Return_btn;
    EditText MachineID_et,IP_et,SqlAccount_et,SqlPassword_et,Password_et;
    CheckBox RFID_cb;
    File file;
    XmlHelper xmlHelper;
    //get the spinner from the xml.
    Spinner spinner;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.connect_setting);

        Confirm_btn=(Button)findViewById(R.id.Confirm_btn);
        Return_btn=(Button)findViewById(R.id.Return_btn);
        MachineID_et=(EditText) findViewById(R.id.MachineID_et);
        IP_et=(EditText)findViewById(R.id.IP_et);
        SqlAccount_et=(EditText)findViewById(R.id.SqlAccount_et);
        SqlPassword_et=(EditText)findViewById(R.id.SqlPassword_et);
        Password_et=(EditText)findViewById(R.id.Password_et);
        spinner= (Spinner) findViewById(R.id.spinner);
        RFID_cb=(CheckBox) findViewById(R.id.RFID_cb);

        ArrayAdapter adapter = ArrayAdapter.createFromResource(this,R.array.ticket_type_array, R.layout.spinner_item);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinner.setAdapter(adapter);

        xmlHelper=new XmlHelper(getFilesDir()+"//connectData.xml");
        file = new File(getFilesDir()+"//connectData.xml");

        if(file.exists()){
            MachineID_et.setText(xmlHelper.ReadValue("MachineID"));
            IP_et.setText(xmlHelper.ReadValue("ServerIP"));
            SqlAccount_et.setText(xmlHelper.ReadValue("sa"));
            SqlPassword_et.setText(xmlHelper.ReadValue("SQLPassWord"));
            Password_et.setText(xmlHelper.ReadValue("SetupPassWord"));
            ArrayAdapter adapter2 = ArrayAdapter.createFromResource(this,R.array.ticket_type_array_value, R.layout.spinner_item);
            spinner.setSelection(adapter2.getPosition(xmlHelper.ReadValue("InOutType")));
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
                int spinner_pos = spinner.getSelectedItemPosition();
                String[] ticket_type_array_value = getResources().getStringArray(R.array.ticket_type_array_value);
                String InOutType = ticket_type_array_value[spinner_pos];
                xmlHelper.WriteValue("InOutType",InOutType);
                xmlHelper.WriteValue("RFID",RFID_cb.isChecked() ? "OPEN":"CLOSE");

                Toast.makeText(ConnectSetting.this, "存檔成功！", Toast.LENGTH_SHORT).show();
                ConnectSetting.this.finish();
            }
        });

        Return_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConnectSetting.this.finish();
            }
        });
    }
}
