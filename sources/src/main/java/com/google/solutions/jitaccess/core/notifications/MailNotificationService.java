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

package com.google.solutions.jitaccess.core.notifications;

import com.google.common.base.Preconditions;
import com.google.common.escape.Escaper;
import com.google.common.html.HtmlEscapers;
import com.google.solutions.jitaccess.core.auth.EmailMapping;
import com.google.solutions.jitaccess.core.clients.SmtpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.stream.Collectors;

/**
 * Concrete class that delivers notifications over SMTP.
 */
public class MailNotificationService extends NotificationService {
  private final @NotNull Options options;
  private final @NotNull EmailMapping emailMapping;
  private final @NotNull SmtpClient smtpClient;

  /**
   * Load a resource from a JAR resource.
   * @return null if not found.
   */
  public static @Nullable String loadResource(String resourceName) throws NotificationException{
    try (var stream = NotificationService.class
      .getClassLoader()
      .getResourceAsStream(resourceName)) {

      if (stream == null) {
        return null;
      }

      var content = stream.readAllBytes();
      if (content.length > 3 &&
        content[0] == (byte)0xEF &&
        content[1] == (byte)0xBB &&
        content[2] == (byte)0xBF) {

        //
        // Strip UTF-8 BOM.
        //
        return new String(content, 3, content.length - 3);
      }
      else {
        return new String(content);
      }
    }
    catch (IOException e) {
      throw new NotificationException(
        String.format("Reading the template %s from the JAR file failed", resourceName), e);
    }
  }

  public MailNotificationService(
    @NotNull SmtpClient smtpClient,
    @NotNull EmailMapping emailMapping,
    @NotNull Options options
  ) {
    Preconditions.checkNotNull(smtpClient);
    Preconditions.checkNotNull(emailMapping);
    Preconditions.checkNotNull(options);

    this.smtpClient = smtpClient;
    this.emailMapping = emailMapping;
    this.options = options;
  }

  // -------------------------------------------------------------------------
  // NotificationService implementation.
  // -------------------------------------------------------------------------

  @Override
  public boolean canSendNotifications() {
    return true;
  }

  @Override
  public void sendNotification(@NotNull Notification notification) throws NotificationException {
    Preconditions.checkNotNull(notification, "notification");

    var htmlTemplate = loadResource(
      String.format("notifications/%s.html", notification.getType()));
    if (htmlTemplate == null) {
      //
      // Unknown kind of notification, ignore.
      //
      return;
    }

    var formattedMessage = new MessageTemplate(
      htmlTemplate,
      this.options.timeZone,
      HtmlEscapers.htmlEscaper())
      .format(notification);

    try {
      this.smtpClient.sendMail(
        notification.getToRecipients()
          .stream()
          .map(id -> this.emailMapping.emailFromUserId(id))
          .collect(Collectors.toList()),
        notification.getCcRecipients()
          .stream()
          .map(id -> this.emailMapping.emailFromUserId(id))
          .collect(Collectors.toList()),
        notification.getSubject(),
        formattedMessage,
        notification.isReply()
          ? EnumSet.of(SmtpClient.Flags.REPLY)
          : EnumSet.of(SmtpClient.Flags.NONE));
    }
    catch (EmailMapping.MappingException e) {
      throw new NotificationException(
        "The notification could not be sent because of an invalid email address", e);
    }
    catch (SmtpClient.MailException e) {
      throw new NotificationException(
        "The notification could not be sent", e);
    }
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  /**
   * Template for turning a notification object into some textual representation.
   */
  public static class MessageTemplate {
    private final @NotNull String template;
    private final @NotNull Escaper escaper;
    private final @NotNull ZoneId timezoneId;

    public MessageTemplate(
      @NotNull String template,
      @NotNull ZoneId timezoneId,
      @NotNull Escaper escaper
    ) {
      Preconditions.checkNotNull(template, "template");
      Preconditions.checkNotNull(timezoneId, "timezoneId");
      Preconditions.checkNotNull(escaper, "escaper");

      this.template = template;
      this.timezoneId = timezoneId;
      this.escaper = escaper;
    }

    public String format(@NotNull NotificationService.Notification notification) {
      Preconditions.checkNotNull(notification, "notification");

      //
      // Replace all {{PROPERTY}} placeholders in the template.
      //

      var message = this.template;
      for (var property : notification.properties.entrySet()) {
        String propertyValue;
        if (property.getValue() instanceof Instant) {
          //
          // Apply time zone and convert to string.
          //
          propertyValue = OffsetDateTime
            .ofInstant((Instant) property.getValue(), this.timezoneId)
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        }
        else if (property.getValue() instanceof Collection<?>) {
          propertyValue = ((Collection<?>)property.getValue()).stream()
            .map(i -> i.toString())
            .collect(Collectors.joining(", "));
        }
        else {
          //
          // Convert to a safe string.
          //
          propertyValue = escaper.escape(property.getValue().toString());
        }

        message = message.replace("{{" + property.getKey() + "}}", propertyValue);
      }

      return message;
    }
  }

  public static class Options {
    public static final ZoneId DEFAULT_TIMEZONE = ZoneOffset.UTC;

    private final @NotNull ZoneId timeZone;

    public Options(@NotNull ZoneId timeZone) {
      Preconditions.checkNotNull(timeZone);
      this.timeZone = timeZone;
    }
  }
}
