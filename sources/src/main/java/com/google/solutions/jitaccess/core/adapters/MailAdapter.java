//
// Copyright 2022 Google LLC
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.google.solutions.jitaccess.core.adapters;

import com.google.common.base.Preconditions;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Adapter for sending email.
 */
public class MailAdapter {
  private final Options options;

  public MailAdapter(Options options) {
    Preconditions.checkNotNull(options, "options");
    this.options = options;
  }

  public void sendMail(
    String recipientName,
    String recipientEmail,
    String subject,
    Multipart content) throws MailException {
    Preconditions.checkNotNull(recipientName, "recipientName");
    Preconditions.checkNotNull(recipientEmail, "recipientEmail");
    Preconditions.checkNotNull(subject, "subject");
    Preconditions.checkNotNull(content, "content");

    Session session = Session.getDefaultInstance(
      this.options.smtpProperties,
      this.options.smtpAuthenticator);

    try {
      var message = new MimeMessage(session);
      message.setFrom(new InternetAddress(this.options.senderAddress, this.options.senderName));
      message.addRecipient(
        Message.RecipientType.TO,
        new InternetAddress(recipientEmail, recipientName));
      message.setSubject(subject);
      message.setContent(content);

      Transport.send(message);
    }
    catch (MessagingException | UnsupportedEncodingException e) {
      throw new MailException("The mail could not be delivered", e);
    }
  }

  public void sendMail(
    String recipientName,
    String recipientEmail,
    String subject,
    String htmlContent) throws MailException {

    try {
      var htmlPart = new MimeBodyPart();
      htmlPart.setContent(htmlContent, "text/html");

      var content = new MimeMultipart();
      content.addBodyPart(htmlPart);

      sendMail(
        recipientName,
        recipientEmail,
        subject,
        content);
    }
    catch (MessagingException e) {
      throw new MailException("The mail could not be formatted", e);
    }
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public static class Options {
    private final String senderName;
    private final String senderAddress;
    private Authenticator smtpAuthenticator = null;
    private final Properties smtpProperties;

    public Options(
      String smtpHost,
      int smtpPort,
      String senderName,
      String senderAddress) {
      Preconditions.checkNotNull(smtpHost, "smtpHost");
      Preconditions.checkNotNull(senderName, "senderName");
      Preconditions.checkNotNull(senderAddress, "senderAddress");

      this.senderName = senderName;
      this.senderAddress = senderAddress;

      this.smtpProperties = new Properties();
      this.smtpProperties.put("mail.smtp.host", smtpHost);
      this.smtpProperties.put("mail.smtp.port", String.valueOf(smtpPort));
    }

    /**
     * Set a JavaMail SMTP property, see
     * https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html.
     */
    public Options setSmtpProperty(String name, String value) {
      Preconditions.checkArgument(name.startsWith("mail.smtp"), "The property is not an SMTP property");
      this.smtpProperties.put(name, value);
      return this;
    }

    /**
     * Add credentials for SMTP authentication.
     */
    public Options setSmtpCredentials(String username, String password) {
      Preconditions.checkNotNull(username, "username");
      Preconditions.checkNotNull(password, "password");

      this.smtpProperties.put("mail.smtp.auth", "true");
      this.smtpAuthenticator = new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(username, password);
        }
      };

      return this;
    }
  }

  public static class MailException extends Exception {
    public MailException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}

