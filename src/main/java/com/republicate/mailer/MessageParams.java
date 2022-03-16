package com.republicate.mailer;

import java.util.List;

public class MessageParams
{
    public String from = null;
    public String to = null;
    public String replyTo = null;
    public String subject = null;
    public String body = null;
    public String type = null;
    public List<String> attachments = null;
    public int nbTry;
    public Object callbackData = null;
    
    public MessageParams (String f, String t, String rt, String s, String b, String tp, List<String> attr, Object cbData)
    {
        from = f;
        to = t;
        replyTo = rt;
        subject = s;
        body = b;
        type = tp;
        attachments = attr;
        callbackData = cbData;
        nbTry = 0;
    }
    
    public String toString()
    {
        return from + " -> "+(to.length()>50?to.substring(0,50)+"...":to)+" subject: '"+subject+"' ("+type+") "+(attachments != null && attachments.size() > 0 ?"with "+attachments.size()+" attachement(s)":"");
    }
      
}
