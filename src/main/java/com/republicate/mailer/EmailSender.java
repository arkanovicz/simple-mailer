package com.republicate.mailer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

public class EmailSender {
    public static void send(String from, String to, String subject, String body) throws IOException {
        send(from, to, subject, body, "text", (List<String>) null);
    }

    public static void send(String from, String to, String cc, String bcc, String replyTo, String subject, String body) throws IOException {
        send(from, to, cc, bcc, replyTo, subject, body, "text", (List<String>) null, null);
    }

    public static void send(String from, String to, String subject, String body, String type) throws IOException {
        send(from, to, subject, body, type, (List<String>) null);
    }

    public static void send(String from, String to, String cc, String bcc, String replyTo, String subject, String body, String type) throws IOException {
        send(from, to, cc, bcc, replyTo, subject, body, type, (List<String>) null, null);
    }

    public static void send(String from, String to, String subject, String body, String type, String att) throws IOException {
        List<String> lst = null;
        if (att != null && !att.isEmpty()) {
            lst = new ArrayList<>();
            lst.add(att);
        }
        send(from, to, subject, body, type, lst);
    }

    public static void send(String from, String to, String subject, String body, String type, List<String> attrLst) throws IOException {
        send(from, to, from, subject, body, type, attrLst);
    }

    public static void send(String from, String to, String replyTo, String subject, String body, String type, List<String> attrlst) throws IOException {
        send(from, to, replyTo, subject, body, type, attrlst, null);
    }

    public static void send(String from, String to, String replyTo, String subject, String body, String type, List<String> attrlst, Object callbackData) throws IOException {
        send(from, to, null, null, replyTo, subject, body, type, attrlst, callbackData);
    }

    public static void send(String from, String to, String cc, String bcc, String replyTo, String subject, String body, String type, List<String> attrlst, Object callbackData) throws IOException {
        send(from, to, cc, bcc, replyTo, subject, body, type, attrlst, null, callbackData);
    }

    public static void send(String from, String to, String cc, String bcc, String replyTo, String subject, String body, String type, Map<String, File> inlineImages) throws IOException {
        send(from, to, cc, bcc, replyTo, subject, body, type, null, inlineImages, null);
    }

    public static void send(String from, String to, String cc, String bcc, String replyTo, String subject, String body, String type, List<String> attrlst, Map<String, File> inlineImages, Object callbackData) throws IOException {
        if (!SmtpLoop.isRunning()) {
            throw new IOException("smtp loop is not running");
        }

        int batchSize = SmtpLoop.getBatchSize();

        if (bcc != null && !bcc.isEmpty()) {
            InternetAddress[] bccAddresses;
            try {
                bccAddresses = InternetAddress.parse(bcc);
            } catch (AddressException e) {
                throw new IOException("invalid BCC address: " + e.getMessage(), e);
            }

            if (bccAddresses.length > batchSize) {
                long delay = SmtpLoop.getBatchDelay();
                int batchCount = (bccAddresses.length + batchSize - 1) / batchSize;
                BatchCallbackData batchCallback = new BatchCallbackData(callbackData, batchCount);

                for (int i = 0; i < batchCount; i++) {
                    int start = i * batchSize;
                    int end = Math.min(start + batchSize, bccAddresses.length);
                    StringBuilder batchBcc = new StringBuilder();
                    for (int j = start; j < end; j++) {
                        if (j > start) batchBcc.append(", ");
                        batchBcc.append(bccAddresses[j].toString());
                    }
                    MessageParams params = new MessageParams(from, i == 0 ? to : null, i == 0 ? cc : null, batchBcc.toString(), replyTo, subject, body, type, attrlst, inlineImages, batchCallback);
                    if (i > 0) params.batchDelay = delay;
                    SmtpLoop.sendMessage(params);
                }
                return;
            }
        }

        MessageParams messageParams = new MessageParams(from, to, cc, bcc, replyTo, subject, body, type, attrlst, inlineImages, callbackData);
        SmtpLoop.sendMessage(messageParams);
    }
}
