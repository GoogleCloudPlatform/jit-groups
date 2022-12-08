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
import com.google.solutions.jitaccess.core.data.UserId;

import javax.enterprise.context.ApplicationScoped;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.EnumSet;
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
    Collection<UserId> toRecipients,
    Collection<UserId> ccRecipients,
    String subject,
    Multipart content,
    EnumSet<Flags> flags
  ) throws MailException {
    Preconditions.checkNotNull(toRecipients, "toRecipients");
    Preconditions.checkNotNull(ccRecipients, "ccRecipients");
    Preconditions.checkNotNull(subject, "subject");
    Preconditions.checkNotNull(content, "content");

    var session = Session.getDefaultInstance(
      this.options.smtpProperties,
      this.options.smtpAuthenticator);

    try {
      var message = new MimeMessage(session);
      message.setFrom(new InternetAddress(this.options.senderAddress, this.options.senderName));

      for (var recipient : toRecipients){
        message.addRecipient(
          Message.RecipientType.TO,
          new InternetAddress(recipient.email, recipient.email));
      }

      for (var recipient : ccRecipients){
        message.addRecipient(
          Message.RecipientType.CC,
          new InternetAddress(recipient.email, recipient.email));
      }

      message.addHeader("Precedence", "bulk");
      message.setContent(content);

      if (flags.contains(Flags.REPLY)) {
        message.setFlag(javax.mail.Flags.Flag.ANSWERED, true);
        message.setSubject("Re: " + subject);
      }
      else {
        message.setSubject(subject);
      }

      Transport.send(message);
    }
    catch (MessagingException | UnsupportedEncodingException e) {
      throw new MailException("The mail could not be delivered", e);
    }
  }

  public void sendMail(
    Collection<UserId> toRecipients,
    Collection<UserId> ccRecipients,
    String subject,
    String htmlContent,
    EnumSet<Flags> flags
  ) throws MailException {
    Preconditions.checkNotNull(toRecipients, "toRecipients");
    Preconditions.checkNotNull(ccRecipients, "ccRecipients");
    Preconditions.checkNotNull(subject, "subject");
    Preconditions.checkNotNull(htmlContent, "htmlContent");

    try {
      var htmlPart = new MimeBodyPart();
      htmlPart.setContent(htmlContent, "text/html");

      var content = new MimeMultipart();
      content.addBodyPart(htmlPart);

      sendMail(
        toRecipients,
        ccRecipients,
        subject,
        content,
        flags);
    }
    catch (MessagingException e) {
      throw new MailException("The mail could not be formatted", e);
    }
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public enum Flags {
    NONE,
    REPLY
  }

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

