package com.example.user.twtc_app;

import android.annotation.SuppressLint;
import android.os.StrictMode;
import android.util.Log;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Created by Jeff.
 */
public class ConnectionClass {
    static String ip="192.168.11.12";
    static String classs = "net.sourceforge.jtds.jdbc.Driver";
    static String db = "TWTC_CDPS";
    static String un = "TWTC";
    static String password = "A#UugHMh";


    @SuppressLint("NewApi")
    public static Connection CONN() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);
        Connection conn = null;
        String ConnURL = null;
        try {
            Class.forName(classs);
            ConnURL = "jdbc:jtds:sqlserver://" + ip + ";"
                    + "databaseName=" + db + ";charset=utf8;user=" + un + ";password="
                    + password + ";";
            conn = DriverManager.getConnection(ConnURL);
        } catch (SQLException ex) {
            ex.printStackTrace();
            WriteLog.appendLog("ConnectionClass.java/CONN/Exception:" + ex.toString());
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            WriteLog.appendLog("ConnectionClass.java/CONN/Exception:" + ex.toString());
        } catch (Exception ex) {
            ex.printStackTrace();
            WriteLog.appendLog("ConnectionClass.java/CONN/Exception:" + ex.toString());
        }
        return conn;
    }
}

