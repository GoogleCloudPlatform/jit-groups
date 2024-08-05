//
// Copyright 2024 Google LLC
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

package com.google.solutions.jitaccess.apis.clients;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.catalog.auth.EmailAddress;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.EnumSet;

/**
 * JavaMail client.
 */
public abstract class MailClient {

  /**
   * Get the sender address to use for email.
   */
  protected abstract InternetAddress senderAddress() throws MailException, IOException;

  /**
   * Create a JavaMail session.
   */
  protected abstract Session createSession() throws MailException, IOException;

  public void sendMail(
    @NotNull Collection<EmailAddress> toRecipients,
    @NotNull Collection<EmailAddress> ccRecipients,
    @NotNull String subject,
    @NotNull Multipart content,
    @NotNull EnumSet<SmtpClient.Flags> flags
  ) throws SmtpClient.MailException {
    Preconditions.checkNotNull(toRecipients, "toRecipients");
    Preconditions.checkNotNull(ccRecipients, "ccRecipients");
    Preconditions.checkNotNull(subject, "subject");
    Preconditions.checkNotNull(content, "content");

    try {
      var session = createSession();

      var message = new MimeMessage(session);
      message.setContent(content);

      message.setFrom(this.senderAddress());

      for (var recipient : toRecipients){
        message.addRecipient(
          Message.RecipientType.TO,
          new InternetAddress(recipient.value(), recipient.value()));
      }

      for (var recipient : ccRecipients){
        message.addRecipient(
          Message.RecipientType.CC,
          new InternetAddress(recipient.value(), recipient.value()));
      }

      //
      // NB. Setting the Precedence header prevents (some) mail readers to not send
      // out of office-replies.
      //
      message.addHeader("Precedence", "bulk");

      if (flags.contains(SmtpClient.Flags.REPLY)) {
        message.setFlag(jakarta.mail.Flags.Flag.ANSWERED, true);
        message.setSubject("Re: " + subject);
      }
      else {
        message.setSubject(subject);
      }

      Transport.send(message);
    }
    catch (MessagingException | IOException e) {
      throw new SmtpClient.MailException("The mail could not be delivered", e);
    }
  }

  public void sendMail(
    @NotNull Collection<EmailAddress> toRecipients,
    @NotNull Collection<EmailAddress> ccRecipients,
    @NotNull String subject,
    @NotNull String htmlContent,
    @NotNull EnumSet<SmtpClient.Flags> flags
  ) throws SmtpClient.MailException {
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
      throw new SmtpClient.MailException("The mail could not be formatted", e);
    }
  }

  public enum Flags {
    NONE,
    REPLY
  }

  public static class MailException extends Exception {
    public MailException(String message, Throwable cause) {
      super(message, cause);
    }
  }

}
