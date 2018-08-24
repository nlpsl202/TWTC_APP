package com.example.user.twtc_app;

/**
 * Created by USER on 2015/11/17.
 */

import android.annotation.SuppressLint;
import android.os.StrictMode;
import android.util.Log;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Created by USER on 2015/11/2.
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
        } catch (SQLException se) {
            Log.e("ERRO", se.getMessage());
        } catch (ClassNotFoundException e) {
            Log.e("ERRO", e.getMessage());
        } catch (Exception e) {
            Log.e("ERRO", e.getMessage());
        }
        return conn;
    }
}

