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
import com.google.solutions.jitaccess.core.adapters.SmtpAdapter;
import com.google.solutions.jitaccess.core.data.UserId;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    private final Options options;
    private final SmtpAdapter smtpAdapter;

    public MailNotificationService(
        SmtpAdapter smtpAdapter,
        Options options
    ) {
      Preconditions.checkNotNull(smtpAdapter);
      Preconditions.checkNotNull(options);

      this.smtpAdapter = smtpAdapter;
      this.options = options;
    }

    @Override
    public boolean canSendNotifications() {
      return true;
    }

    @Override
    public void sendNotification(Notification notification) throws NotificationException {
      Preconditions.checkNotNull(notification, "notification");

      try {
        this.smtpAdapter.sendMail(
          notification.toRecipients,
          notification.ccRecipients,
          notification.subject,
          notification.formatMessage(this.options.timeZone),
          notification.isReply()
            ? EnumSet.of(SmtpAdapter.Flags.REPLY)
            : EnumSet.of(SmtpAdapter.Flags.NONE));
      }
      catch (SmtpAdapter.MailException e) {
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

    protected final Map<String, Object> properties = new HashMap<>();

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

        if (stream == null) {
          throw new RuntimeException(
            String.format("The JAR file does not contain an template named %s", resourceName));
        }

        var content = stream.readAllBytes();
        if (content.length > 3 &&
          content[0] == (byte)0xEF &&
          content[1] == (byte)0xBB &&
          content[2] == (byte)0xBF) {
          //
          // Strip UTF-8 BOM
          //
          return new String(content, 3, content.length - 3);
        }
        else {
          return new String(content);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(
          String.format("Reading the template %s from the JAR file failed", resourceName), e);
      }
    }

    protected String formatMessage(ZoneId zone) {
      //
      // Read email template file from JAR and replace {{PROPERTY}} placeholders.
      //
      var escaper = HtmlEscapers.htmlEscaper();

      var message = this.template;
      for (var property : this.properties.entrySet()) {
        String propertyValue;
        if (property.getValue() instanceof Instant) {
          //
          // Apply time zone.
          //
          propertyValue = OffsetDateTime
            .ofInstant((Instant)property.getValue(), zone)
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        }
        else {
          propertyValue = escaper.escape(property.getValue().toString());
        }

        message = message.replace("{{" + property.getKey() + "}}", propertyValue);
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
    public static final ZoneId DEFAULT_TIMEZONE = ZoneOffset.UTC;

    private final ZoneId timeZone;

    public Options(ZoneId timeZone) {
      Preconditions.checkNotNull(timeZone);
      this.timeZone = timeZone;
    }
  }

  public static class NotificationException extends Exception {
    public NotificationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
