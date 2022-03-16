package com.republicate.mailer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EmailSender
{
    public static void send(String from,String to,String subject,String body) throws IOException
    {
        send(from,to,subject,body,"text",(List<String>)null);
    }

    public static void send(String from,String to,String subject,String body,String type) throws IOException
    {
        send(from,to,subject,body,type,(List<String>)null);
    }

    public static void send(String from,String to,String subject,String body,String type,String att) throws IOException
    {
        List<String> lst = null;
        if(att != null && att.length() > 0)
        {
            lst = new ArrayList<String>();
            lst.add(att);
        }
        send(from,to,subject,body,type,lst);
    }

    public static void send(String from,String to,String subject,String body,String type,List<String> attrLst) throws IOException
    {
        send(from, to, from, subject, body, type, attrLst);
    }

    public static void send(String from, String to, String replyTo, String subject, String body, String type, List<String> attrlst) throws IOException
    {
        send(from, to, replyTo, subject, body, type, attrlst, null);
    }

    public static void send(String from, String to, String replyTo, String subject, String body, String type, List<String> attrlst, Object callbackData) throws IOException
    {
        if (!SmtpLoop.isRunning())
        {
            throw new IOException("smtp loop is not running");
        }
        MessageParams messageParams = new MessageParams(from,to,replyTo,subject,body,type,attrlst,callbackData);
        SmtpLoop.sendMessage(messageParams);
    }
}
