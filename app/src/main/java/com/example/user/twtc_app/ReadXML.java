package com.example.user.twtc_app;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class ReadXML {
    static File file= new File("//data//user//0//com.example.user.twtc_app//files//connectData.xml");

    public static String ReadValue(String key){
        String value="";
        if(file.exists()){
            Document dom;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {
                DocumentBuilder db = dbf.newDocumentBuilder();
                dom = db.parse(file);
                Element doc = dom.getDocumentElement();

                value=getTextValue("", doc, key);
            } catch (ParserConfigurationException pce) {
                System.out.println(pce.getMessage());
            } catch (SAXException se) {
                System.out.println(se.getMessage());
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
            }
        }
        return value;
    }

    public static String getTextValue(String def, Element doc, String tag) {
        String value = def;
        NodeList nl;
        nl = doc.getElementsByTagName(tag);
        if (nl.getLength() > 0 && nl.item(0).hasChildNodes()) {
            value = nl.item(0).getFirstChild().getNodeValue();
        }
        return value;
    }
}
