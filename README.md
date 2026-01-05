<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Simple Mailer

A lightweight, asynchronous Java email sending library built on Jakarta Mail.

## Features

- Asynchronous email delivery via background SMTP thread
- Multiple content types: plain text, HTML, and URL-based content
- Full recipient support: TO, CC, BCC, Reply-To
- File and URL attachments
- Automatic inline image embedding for HTML emails
- HTML-to-plain-text conversion for multipart alternatives
- SSL/TLS support with optional hostname verification bypass (proxy/tunnel scenarios)
- Automatic retry mechanism (up to 3 attempts, 1-hour delay between retries)
- Optional delivery status callbacks

## Installation

### Maven

```xml
<dependency>
    <groupId>com.republicate</groupId>
    <artifactId>simple-mailer</artifactId>
    <version>2.5</version>
</dependency>
```

## Quick Start

```java
import com.republicate.mailer.SmtpLoop;
import com.republicate.mailer.EmailSender;

// 1. Configure and start the SMTP loop at application startup
Properties config = new Properties();
config.put("smtp.host", "smtp.example.com");
config.put("smtp.port", "587");
config.put("smtp.user", "username");
config.put("smtp.password", "password");

new Thread(new SmtpLoop(config), "smtp").start();

// 2. Send emails
EmailSender.send(
    "sender@example.com",
    "recipient@example.com",
    "Hello World",
    "This is the email body."
);
```

## Configuration

SMTP configuration is passed as a `Properties` object to the `SmtpLoop` constructor.

| Property | Required | Description |
|----------|----------|-------------|
| `smtp.host` | Yes | SMTP server hostname |
| `smtp.port` | Yes | SMTP port (465 for SSL, 587 for STARTTLS) |
| `smtp.user` | No | SMTP authentication username |
| `smtp.password` | No | SMTP authentication password |
| `smtp.sslCheck` | No | SSL hostname verification (`true` default, `false` for proxies/tunnels) |
| `smtp.debug` | No | Enable debug logging (`true`/`false`) |
| `webapp.env` | No | If set to `prod`, forces authentication |

### SSL/TLS Behavior

- **Port 465**: Uses implicit SSL (`mail.smtp.ssl.enable=true`)
- **Port 587**: Uses STARTTLS (`mail.smtp.starttls.enable=true`, `mail.smtp.starttls.required=true`)

### Disabling SSL Hostname Verification

When using SMTP through a proxy or SSH tunnel, the server certificate won't match the connection hostname. Use `smtp.sslCheck=false` to disable hostname verification:

```java
config.put("smtp.host", "172.17.0.1");    // Connection through tunnel
config.put("smtp.port", "58700");
config.put("smtp.sslCheck", "false");     // Disable hostname verification
```

**Note:** Only disable this in controlled environments (local tunnels, trusted proxies). For production, ensure the certificate matches the connection hostname.

## Usage Examples

### Plain Text Email

```java
EmailSender.send(
    "from@example.com",
    "to@example.com",
    "Subject",
    "Plain text body"
);
```

### HTML Email

HTML emails automatically include a plain-text alternative.

```java
EmailSender.send(
    "from@example.com",
    "to@example.com",
    "Subject",
    "<html><body><h1>Hello</h1><p>HTML content here.</p></body></html>",
    "html"
);
```

### URL-Based Content

Fetch HTML content from a URL:

```java
EmailSender.send(
    "from@example.com",
    "to@example.com",
    "Newsletter",
    "https://example.com/newsletter.html",
    "url"
);
```

### With CC, BCC and Reply-To

```java
EmailSender.send(
    "from@example.com",
    "to@example.com",
    "cc@example.com",
    "bcc@example.com",
    "replyto@example.com",
    "Subject",
    "Body content"
);
```

### With Attachments

```java
List<String> attachments = Arrays.asList(
    "/path/to/document.pdf",
    "https://example.com/file.pdf"
);

EmailSender.send(
    "from@example.com",
    "to@example.com",
    "replyto@example.com",
    "Subject",
    "Please find attached documents.",
    "text",
    attachments
);
```

### Multiple Recipients

Comma-separated addresses are supported:

```java
EmailSender.send(
    "from@example.com",
    "user1@example.com, user2@example.com, user3@example.com",
    "Subject",
    "Body"
);
```

### Delivery Status Callback

```java
import com.republicate.mailer.CompletionCallback;

SmtpLoop.setCompletionCallback((status, data) -> {
    switch (status) {
        case DELIVERED:
            System.out.println("Email delivered: " + data);
            break;
        case NOT_DELIVERED:
            System.err.println("Delivery failed: " + data);
            break;
        case PARTIALLY_DELIVERED:
            System.out.println("Partial delivery: " + data);
            break;
    }
});

// Pass callback data with the email
EmailSender.send(
    "from@example.com",
    "to@example.com",
    "replyto@example.com",
    "Subject",
    "Body",
    "text",
    null,                    // no attachments
    "order-confirmation-123" // callback data
);
```

## API Reference

### EmailSender

Static facade providing multiple overloaded `send()` methods.

```
send(from, to, subject, body)
send(from, to, subject, body, type)
send(from, to, subject, body, type, attachment)
send(from, to, subject, body, type, attachmentList)
send(from, to, replyTo, subject, body, type, attachmentList)
send(from, to, replyTo, subject, body, type, attachmentList, callbackData)
send(from, to, cc, bcc, replyTo, subject, body)
send(from, to, cc, bcc, replyTo, subject, body, type)
send(from, to, cc, bcc, replyTo, subject, body, type, attachmentList, callbackData)
```

**Parameters:**
- `from` - Sender email address
- `to` - Recipient(s), comma-separated
- `cc` - CC recipient(s), comma-separated (optional)
- `bcc` - BCC recipient(s), comma-separated (optional)
- `replyTo` - Reply-To address
- `subject` - Email subject
- `body` - Email body content
- `type` - Content type: `"text"`, `"html"`, or `"url"`
- `attachmentList` - List of file paths or URLs
- `callbackData` - Arbitrary data passed to completion callback

### SmtpLoop

Background thread managing the SMTP connection and message queue.

**Constructor:**
```java
SmtpLoop(Properties config)
```

**Static methods:**
```java
boolean isRunning()
void setCompletionCallback(CompletionCallback callback)
```

**Usage:**
```java
new Thread(new SmtpLoop(config), "smtp").start();
```

### CompletionCallback

Interface for tracking delivery status.

```java
public interface CompletionCallback {
    enum Status { DELIVERED, NOT_DELIVERED, PARTIALLY_DELIVERED }
    void deliveryCompleted(Status status, Object data);
}
```

## Architecture

```
EmailSender (public API)
      |
      v
  SmtpLoop (background thread)
      |
      +-- queue (ConcurrentLinkedDeque) --> generateMessage --> Transport.send
      |
      +-- retry queue (failed messages, retried after 1 hour)
```

**Timing:**
- Queue poll interval: 30 seconds
- Retry delay: 1 hour
- Max retries: 3
- Connection timeout: 10 seconds

**HTML Processing:**
- Inline images (`<img src="...">`) are automatically extracted and embedded as MIME parts
- HTML content is converted to plain text for multipart/alternative

## Dependencies

- [Angus Mail](https://eclipse-ee4j.github.io/angus-mail/) (Jakarta Mail implementation) 2.0.3
- [SLF4J](https://www.slf4j.org/) 1.7.36
- [Apache Commons Lang3](https://commons.apache.org/proper/commons-lang/) 3.19.0
- [jsoup](https://jsoup.org/) 1.21.2

## Requirements

- Java 8+
- Maven 3.0.5+

## Building

```bash
mvn install
```

## License

Apache License 2.0

## Author

Claude Brisson - [republicate.com](https://republicate.com)
