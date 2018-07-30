package com.example.user.twtc_app;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class XmlHelper {
    static File file;
    String filePath="";

    public XmlHelper(String path){
        filePath=path;
        file= new File(path);
    }
    public void WriteValue(String Key,String Value){
        try {
            Boolean nodeExist=false;
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse("file://"+filePath);

            Node setup = doc.getFirstChild();

            // loop the setup child node
            NodeList list = setup.getChildNodes();

            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);

                //判斷資料名稱是否已存在
                if (Key.equals(node.getNodeName())) {
                    nodeExist=true;
                    node.setTextContent(Value);
                }
            }

            if(!nodeExist){
                // append a new node to setup
                Element el = doc.createElement(Key);
                el.appendChild(doc.createTextNode(Value));
                setup.appendChild(el);
                setup.appendChild(doc.createTextNode("\n\t"));
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filePath));
            transformer.transform(source, result);
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (TransformerException tfe) {
            tfe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (SAXException sae) {
            sae.printStackTrace();
        }
    }

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
