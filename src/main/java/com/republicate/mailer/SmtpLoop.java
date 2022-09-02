package com.republicate.mailer;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.Map;
import java.util.Properties;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.BodyPart;
import javax.mail.event.TransportEvent;
import javax.mail.event.TransportListener;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmtpLoop implements Runnable, TransportListener
{
    protected static final long SLEEP_DELAY = 30000; // 30 seconds
    protected static final long RETRY_DELAY = 3600000;  // 1 hour
    protected static final int MAX_RETRIES = 3;

    protected static Logger logger = LoggerFactory.getLogger("smtp-loop");
    protected static HtmlToPlainText htmlToPlainText = new HtmlToPlainText();

    private static CompletionCallback completionCallback = null;
    public static void setCompletionCallback(CompletionCallback callback)
    {
        completionCallback = callback;
    }

    private Deque<MessageParams> queue = new ConcurrentLinkedDeque<MessageParams>();
    private Deque<MessageParams> retry = new ConcurrentLinkedDeque<MessageParams>();
    private static SmtpLoop zeInstance = null;
    private Transport transport = null;
    private Properties props = null;
    private Session session = null;

    public SmtpLoop(Properties config)
    {
        if (zeInstance != null) throw new RuntimeException("SmtpLoop already instanciated");
        zeInstance = this;
        boolean doAuth = config.containsKey("smtp.user") && config.containsKey("smtp.password") &&
            !config.getProperty("smtp.user").isEmpty() && !config.getProperty("smtp.password").isEmpty();
        doAuth = doAuth || "prod".equals(config.getProperty("webapp.env"));
        props = new Properties();
        props.put("mail.smtp.host", config.getProperty("smtp.host"));
        props.put("mail.smtp.auth", String.valueOf(doAuth));
        //props.put("mail.smtp.port", Integer.parseInt(config.getProperty("smtp.port")));
        props.put("mail.smtp.port", config.getProperty("smtp.port"));
        if (logger.isDebugEnabled())
        {
            logger.debug("SMTP loop configuration:");
            for (String prop : props.stringPropertyNames())
            {
                logger.debug("  {} = {}", prop, props.getProperty(prop));
            }
        }
        final String user = config.getProperty("smtp.user");
        final String password = config.getProperty("smtp.password");
        Authenticator auth = null;
        if (doAuth)
        {
            props.put("mail.smtp.starttls.enable","true");
            auth = new Authenticator()
            {
                @Override
                protected PasswordAuthentication getPasswordAuthentication()
                {
                    return new PasswordAuthentication(user, password);
                }
            };
        }
        session = Session.getInstance(props, auth);
    }

    private int addMail(MessageParams params)
    {
        logger.debug("adding to queue: {}", params);
        queue.addLast(params);
        logger.debug("current queue length is {}", queue.size());

        synchronized(this)
        {
            notify();
        }

        return queue.size();
    }

    private boolean running = false;

    public static boolean isRunning()
    {
        return zeInstance != null && zeInstance.running;
    }

    public static void sendMessage(MessageParams params)
    {
        if (zeInstance == null || !zeInstance.isRunning())
        {
            logger.warn("SMTP loop is not running: ignoring message: {}", params);
        }
        else
        {
            zeInstance.addMail(params);
        }
    }

    public String extractImages(String html, List<String> url)
    {
        Pattern pattern = Pattern.compile("<img\\s+[^>]*\\s*src=\"([^\"]+)\"[^>]*>",Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        StringBuffer buffer = new StringBuffer();
        int index = 0;
        while(matcher.find())
        {
            url.add(matcher.group(1));
            matcher.appendReplacement(buffer, "<img src=\"cid:image_"+index+"@internetgoschool.com\"/>");
            ++index;
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public Message generateMessage(MessageParams params)
            throws Exception
    {
        if (transport == null)
        {
            transport = session.getTransport("smtp");
            transport.addTransportListener(this);
        }
        Message message = new MimeMessage(session);
        InternetAddress sender = new InternetAddress(StringEscapeUtils.unescapeHtml4(params.from));
        message.setFrom(sender);
        message.setSentDate(new Date());
        InternetAddress dest[] = InternetAddress.parse(StringEscapeUtils.unescapeHtml4(params.to));
        for(InternetAddress d:dest)
        {
            message.addRecipient(Message.RecipientType.TO,d);
        }
        InternetAddress replyTo[] = InternetAddress.parse(StringEscapeUtils.unescapeHtml4(params.replyTo));
        message.setReplyTo(replyTo);

        message.setSubject(StringEscapeUtils.unescapeHtml4(params.subject));

        String html = null;
        String text = null;

        if(params.type.equals("url"))
        {
            html = loadUrl(params.body);
        }
        else if (params.type.equals("html"))
        {
            html = params.body;
        }
        else if (params.type.equals("text"))
        {
            text = StringEscapeUtils.unescapeHtml4(params.body);
        }

        MimeMultipart mpart = new MimeMultipart();

        if(html == null)
        {
            MimeBodyPart body = new MimeBodyPart();
            body.setText(StringEscapeUtils.unescapeHtml4(params.body), "utf-8");
            mpart.addBodyPart(body);
        }
        else
        {
            mpart = new MimeMultipart("alternative");
            text = htmlToPlainText.getPlainText(html);
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(text);
            mpart.addBodyPart(textPart);

            List<String> imagesURL = new ArrayList<String>();
            html = extractImages(html, imagesURL);

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(html, "text/html; charset=utf-8");

            if(imagesURL.size() == 0)
            {
                mpart.addBodyPart(htmlPart);
            }
            else
            {
                MimeMultipart related = new MimeMultipart("related; type=\"text/html\"");

                related.addBodyPart(htmlPart);
                int index = 0;
                for(String url: imagesURL)
                {
                    // TODO CB - use parametrized protocol
                    if(url.startsWith("ftp://")) url = url.replaceFirst("^ftp","https");
                    // do not rewrite protocols: they should already be ok
                    // else if (url.startsWith("http://")) url = url.replaceFirst("^http","https");
                    else if(!url.startsWith("https"))
                    {
                        // ".." is not handled
                        if(!url.startsWith("/")) url = "/" + url;
                        url = "https://internetgoschool.com" + url;
                    }
                    InputStream input = new URL(url).openStream();
                    //String mime = URLConnection.guessContentTypeFromStream(input);
                    String mime = URLConnection.guessContentTypeFromName(url.substring(url.lastIndexOf(File.separator)+1));
                    DataSource source = new ByteArrayDataSource(input, mime);
                    MimeBodyPart inlineImage = new MimeBodyPart();
                    inlineImage.setDataHandler(new DataHandler(source));
                    inlineImage.setFileName(url.substring(url.lastIndexOf(File.separator)+1));
                    inlineImage.setContentID("<image_"+index+"@internetgoschool.com>");
                    related.addBodyPart(inlineImage);
                    ++index;
                }
                MimeBodyPart wrapper = new MimeBodyPart();
                wrapper.setContent(related);
                mpart.addBodyPart(wrapper);
            }
        }

        if(params.attachments != null)
        {
            if(html == null)
            {
                mpart.setSubType("mixed");
            }
            else
            {
                MimeMultipart alternative = mpart;
                mpart = new MimeMultipart("mixed");
                MimeBodyPart wrapper = new MimeBodyPart();
                wrapper.setContent(alternative);
                mpart.addBodyPart(wrapper);
            }
            for(String path:params.attachments)
            {
                BodyPart bPart = new MimeBodyPart();
                DataSource source = null;
                if(path.startsWith("http://") || path.startsWith("https://"))
                {
                    String mime = (path.endsWith("pdf")) ? "application/pdf" : "text/html";
                    source = new ByteArrayDataSource(new URL(path).openStream(), mime);
                }
                else
                {
                    source = new FileDataSource(path);
                }
                bPart.setDataHandler(new DataHandler(source));
                bPart.setFileName(path.substring(path.lastIndexOf(File.separator)+1));
                mpart.addBodyPart(bPart);
            }
        }

        message.setContent(mpart);

        return message;
    }

    /* to be able to asynchronously get message params from message */
    private Map<Message,MessageParams> paramsMap = new ConcurrentHashMap<Message,MessageParams>();

      private void connectTransport() throws Exception
      {
          ExecutorService service = Executors.newSingleThreadExecutor();
          final Future<Exception> result = service.submit(new Callable<Exception>()
          {
              public Exception call()
              {
                  try
                  {
                      logger.info("Connecting SMTP transport at {}:{} {}", props.getProperty("mail.smtp.host"), props.getProperty("mail.smtp.port"), "true".equals(props.getProperty("mail.smtp.auth")) ? "with auth" : "");
                      transport.connect();
                  }
                  catch (Exception e)
                  {
                      return e;
                  }
                  return null;
              }
          });
          Exception ex = null;
          try
          {
              ex = result.get(10, TimeUnit.SECONDS);
          }
          catch (InterruptedException | ExecutionException | TimeoutException e)
          {
              ex = e;
          }
          finally
          {
              service.shutdown();
          }
          if (ex != null) throw ex;
      }
      
    public void run()
    {
        logger.debug("starting send loop");
        try
        {
            running = true;
            while (running)
            {
                while (running && queue.size() > 0)
                {
                    boolean failed = false;
                    MessageParams params = null;
                    Message message = null;
                    int illegalStateTries = 0;
                    try
                    {
                        params = queue.removeFirst();
                        message = generateMessage(params);
                        int tries = 0;
                        while (!transport.isConnected())
                        {
                            try
                            {
                                logger.debug("smtp: connecting to transport");
                                connectTransport();
                                logger.debug("smtp: connected to transport");
                            }
                            catch (Exception e)
                            {
                                logger.error("smtp: could not connect to transport", e);
                                if (++tries > MAX_RETRIES)
                                {
                                    running = false;
                                    throw e;
                                }
                                Thread.sleep(5000);
                            }
                        }
                        logger.debug("smtp: sending message "+params);
                        paramsMap.put(message, params);
                        transport.sendMessage(message, message.getAllRecipients());
                    }
                    catch(SendFailedException SFe)
                    {
                        failed = true;
                        logger.error("could not send message {}", params, SFe);
                    }
                    catch (IllegalStateException ise)
                    {
                        failed = true;
                        logger.error("could not send message {}", params, ise);
                        if (++illegalStateTries > MAX_RETRIES)
                        {
                            running = false;
                            throw ise;
                        }
                    }
                    catch (InterruptedException ie)
                    {
                        logger.debug("Exiting smtp loop...");
                        running = false;
                        return;
                    }
                    catch(Throwable t)
                    {
                        failed = true;
                        logger.error("could not send message ", params, t);
                    }

                    if (failed)
                    {
                        if (params.nbTry < MAX_RETRIES)
                        {
                            params.nbTry++;
                            retry.addLast(params);
                        }
                        if (message != null) cleanupMessage(message);
                    }
                    else
                    {
                        logger.debug("sending in progress: {}", params);
                        if (completionCallback != null)
                        {
                            completionCallback.deliveryCompleted(CompletionCallback.Status.DELIVERED, params.callbackData);
                        }
                        while (retry.size() > 0)
                        {
                            queue.addLast(retry.removeFirst());
                        }
                    }
                    if (Thread.currentThread().isInterrupted()) break;
                }

                long exitTime = System.currentTimeMillis( );

                try
                {
                    synchronized(this)
                    {
                        wait(SLEEP_DELAY);
                    }
                }
                catch (InterruptedException e)
                {
                    session = null;
                    break;
                }

                if (((System.currentTimeMillis( ) - exitTime) > RETRY_DELAY) && (retry.size() > 0))
                {
                    while (retry.size() > 0)
                    {
                        queue.addLast(retry.removeFirst());
                    }
                }
                if (Thread.currentThread().isInterrupted()) return;
            }
        }
        catch(Exception e)
        {
            logger.error("unrecoverable error in smtp loop", e);
        }
        finally
        {
            running = false;
            logger.info("exiting");
        }
    }

    // TransportListener

    public void messageDelivered(TransportEvent e)
    {
        try
        {
            logEvent(e);
            if (completionCallback != null) doCallBack(CompletionCallback.Status.DELIVERED, e.getMessage());
            transport.close();
        }
        catch (MessagingException me)
        {
            // ignored
        }
    }

    public void messageNotDelivered(TransportEvent e)
    {
        try
        {
            logEvent(e);
            if (completionCallback != null) doCallBack(CompletionCallback.Status.NOT_DELIVERED, e.getMessage());
            transport.close();
        }
        catch (MessagingException me)
        {
            // ignored
        }
    }

    public void messagePartiallyDelivered(TransportEvent e)
    {
        try
        {
            logEvent(e);
            if (completionCallback != null) doCallBack(CompletionCallback.Status.PARTIALLY_DELIVERED, e.getMessage());
            transport.close();
        }
        catch (MessagingException me)
        {
            // ignored
        }
    }

    protected void cleanupMessage(Message message)
    {
        paramsMap.remove(message);
    }

    protected void doCallBack(CompletionCallback.Status status, Message message)
    {
        MessageParams params = paramsMap.get(message);
        if (completionCallback != null) completionCallback.deliveryCompleted(status, params.callbackData);
        cleanupMessage(message);
    }

    public void logEvent(TransportEvent e) throws MessagingException
    {
        Message msg = e.getMessage();

        String disp = "message ";
        switch (e.getType())
        {
            case TransportEvent.MESSAGE_DELIVERED:
                disp += "successfully delivered";
                break;
            case TransportEvent.MESSAGE_NOT_DELIVERED:
                disp += "not delivered";
                break;
            case TransportEvent.MESSAGE_PARTIALLY_DELIVERED:
                disp += "partially delivered";
                break;
        }
        disp += ": " + msg.getSubject();

        logger.info(disp);

        Address[] addresses;
        addresses = e.getValidSentAddresses();
        if (addresses != null && addresses.length > 0) logger.info("valid sent adresses: {}", join(addresses));
        addresses = e.getValidUnsentAddresses();
        if (addresses != null && addresses.length > 0) logger.info("valid unsent adresses: {}", join(addresses));
        addresses = e.getInvalidAddresses();
        if (addresses != null && addresses.length > 0) logger.info("invalid adresses: {}", join(addresses));
    }

    public String loadUrl(String inUrl) throws Exception
    {
        return loadUrl(new URL(inUrl));
    }

    public String loadUrl(URL inUrl) throws Exception
    {
        String result = "";
        String line;
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                inUrl.openStream()));
        while ((line = reader.readLine()) != null)
        {
            result += line + "\n";
        }
        return result;
    }

    public static String join(Address[] add)
    {
        String result = "";
        for (int i=0;i<add.length;i++) {
            if (i>0) result+=",";
            result += add[i];
        }
        return result;
    }
}

