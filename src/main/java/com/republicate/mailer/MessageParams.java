package com.republicate.mailer;

import java.util.List;

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
    public int nbTry;
    public Object callbackData = null;
    
    public MessageParams (String from, String to, String cc, String bcc, String replyTo, String subject, String body, String type, List<String> attachments, Object callbackData)
    {
        this.from = from;
        this.to = to;
        this.cc = cc;
        this.bcc = body;
        this.replyTo = replyTo;
        this.subject = subject;
        this.body = body;
        this.type = type;
        this.attachments = attachments;
        this.callbackData = callbackData;
        this.nbTry = 0;
    }
    
    public String toString()
    {
        return from + " -> "+(to.length()>50?to.substring(0,50)+"...":to)+" subject: '"+subject+"' ("+type+") "+(attachments != null && attachments.size() > 0 ?"with "+attachments.size()+" attachement(s)":"");
    }
      
}
