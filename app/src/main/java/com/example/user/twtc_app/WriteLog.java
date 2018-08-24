package com.example.user.twtc_app;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by Jeff.
 */
public class WriteLog
{
    public static void appendLog(String text)
    {
        String logFilePath = "//storage//sdcard0";
        File logFile = new File(logFilePath, getDate()+".txt");
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(getDateTime()+"  "+text);
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    //取得現在時間
    public static String getDateTime(){
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Calendar c = Calendar.getInstance();
        String str = df.format(c.getTime());
        return str;
    }
    //取得現在時間
    public static String getDate(){
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Calendar c = Calendar.getInstance();
        String str = df.format(c.getTime());
        return str;
    }
}
