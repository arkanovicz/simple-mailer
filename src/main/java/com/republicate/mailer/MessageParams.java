package com.republicate.mailer;

import java.io.File;
import java.util.List;
import java.util.Map;

public class MessageParams
{
    public String from = null;
    public String to = null;
    public String cc = null;
    public String bcc = null;
    public String replyTo = null;
    public String subject = null;
    public String body = null;
    public String type = null;
    public List<String> attachments = null;
    public Map<String, File> inlineImages = null;
    public long batchDelay = 0;
    public int nbTry;
    public Object callbackData = null;

    public MessageParams (String from, String to, String cc, String bcc, String replyTo, String subject, String body, String type, List<String> attachments, Object callbackData)
    {
        this(from, to, cc, bcc, replyTo, subject, body, type, attachments, null, callbackData);
    }

    public MessageParams (String from, String to, String cc, String bcc, String replyTo, String subject, String body, String type, List<String> attachments, Map<String, File> inlineImages, Object callbackData)
    {
        this.from = from;
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.replyTo = replyTo;
        this.subject = subject;
        this.body = body;
        this.type = type;
        this.attachments = attachments;
        this.inlineImages = inlineImages;
        this.callbackData = callbackData;
        this.nbTry = 0;
    }
    
    public String toString()
    {
        String dest = to != null ? to : bcc;
        return from + " -> "+(dest != null && dest.length()>50?dest.substring(0,50)+"...":dest)+" subject: '"+subject+"' ("+type+") "+(attachments != null && attachments.size() > 0 ?"with "+attachments.size()+" attachement(s)":"");
    }
      
}
