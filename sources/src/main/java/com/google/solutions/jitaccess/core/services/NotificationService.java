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

package com.google.solutions.jitaccess.core.services;

import com.google.common.base.Preconditions;
import com.google.common.html.HtmlEscapers;
import com.google.solutions.jitaccess.core.adapters.MailAdapter;
import com.google.solutions.jitaccess.core.data.UserId;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for delivering notifications.
 */
@ApplicationScoped
public abstract class NotificationService {

  public abstract void sendNotification(Notification notification) throws NotificationException;

  public abstract boolean canSendNotifications();

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public static class MailNotificationService extends NotificationService {
    private final MailAdapter mailAdapter;

    public MailNotificationService(MailAdapter mailAdapter) {
      Preconditions.checkNotNull(mailAdapter);
      this.mailAdapter = mailAdapter;
    }

    @Override
    public boolean canSendNotifications() {
      return true;
    }

    @Override
    public void sendNotification(Notification notification) throws NotificationException {
      Preconditions.checkNotNull(notification, "notification");

      try {
        this.mailAdapter.sendMail(
          notification.toRecipients,
          notification.ccRecipients,
          notification.subject,
          notification.formatMessage(),
          notification.isReply()
            ? EnumSet.of(MailAdapter.Flags.REPLY)
            : EnumSet.of(MailAdapter.Flags.NONE));
      }
      catch (MailAdapter.MailException e) {
        throw new NotificationException("The notification could not be sent", e);
      }
    }
  }

  public static class SilentNotificationService extends NotificationService {
    @Override
    public boolean canSendNotifications() {
      return false;
    }

    @Override
    public void sendNotification(Notification notification) throws NotificationException {
      //
      // Print it so that we can see the message during development.
      //
      System.out.println(notification);
    }
  }

  /** Generic notification that can be formatted as a (HTML) email */
  public static abstract class Notification {
    private final String template;
    private final Collection<UserId> toRecipients;
    private final Collection<UserId> ccRecipients;
    private final String subject;

    protected final Map<String, String> properties = new HashMap<>();

    protected boolean isReply() {
      return false;
    }

    protected Notification(
      String template,
      Collection<UserId> toRecipients,
      Collection<UserId> ccRecipients,
      String subject
    ) {
      Preconditions.checkNotNull(template, "template");
      Preconditions.checkNotNull(toRecipients, "toRecipients");
      Preconditions.checkNotNull(ccRecipients, "ccRecipients");
      Preconditions.checkNotNull(subject, "subject");

      this.template = template;
      this.toRecipients = toRecipients;
      this.ccRecipients = ccRecipients;
      this.subject = subject;
    }

    protected static String loadMessageTemplate(String resourceName) {
      try (var stream = NotificationService.class
        .getClassLoader()
        .getResourceAsStream(resourceName)) {

        return new String(stream.readAllBytes());
      }
      catch (IOException e) {
        throw new RuntimeException(
          String.format("The JAR file does not contain an template named %s", resourceName), e);
      }
    }

    protected String formatMessage() {
      //
      // Read email template file from JAR and replace {{PROPERTY}} placeholders.
      //
      var escaper = HtmlEscapers.htmlEscaper();

      var message = this.template;
      for (var property : this.properties.entrySet()) {
        message = message.replace(
          "{{" + property.getKey() + "}}",
          escaper.escape(property.getValue()));
      }

      return message;
    }

    @Override
    public String toString() {
      return String.format(
        "Notification to %s: %s\n\n%s",
        this.toRecipients.stream().map(e -> e.email).collect(Collectors.joining(", ")),
        this.subject,
        this.properties
          .entrySet()
          .stream()
          .map(e -> String.format(" %s: %s", e.getKey(), e.getValue()))
          .collect(Collectors.joining("\n", "", "")));
    }
  }

  public static class Options {
    private final boolean enableEmail;

    public Options(boolean enableEmail) {
      this.enableEmail = enableEmail;
    }
  }

  public static class NotificationException extends Exception {
    public NotificationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
