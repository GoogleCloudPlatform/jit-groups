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
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.adapters.SmtpAdapter;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;

/**
 * Concrete class that delivers notifications over SMTP.
 */
public class MailNotificationService extends NotificationService {
  private final Options options;
  private final SmtpAdapter smtpAdapter;

  /**
   * Load a resource from a JAR resource.
   * @return null if not found.
   */
  public static String loadResource(String resourceName) throws NotificationException{
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
    SmtpAdapter smtpAdapter,
    Options options
  ) {
    Preconditions.checkNotNull(smtpAdapter);
    Preconditions.checkNotNull(options);

    this.smtpAdapter = smtpAdapter;
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
  public void sendNotification(Notification notification) throws NotificationException {
    Preconditions.checkNotNull(notification, "notification");

    var htmlTemplate = loadResource(
      String.format("notifications/%s.html", notification.getTemplateId()));
    if (htmlTemplate == null) {
      //
      // Unknown kind of notification, ignore.
      //
      return;
    }

    var formattedMessage = new NotificationTemplate(
      htmlTemplate,
      this.options.timeZone,
      HtmlEscapers.htmlEscaper())
      .format(notification);

    try {
      this.smtpAdapter.sendMail(
        notification.getToRecipients(),
        notification.getCcRecipients(),
        notification.getSubject(),
        formattedMessage,
        notification.isReply()
          ? EnumSet.of(SmtpAdapter.Flags.REPLY)
          : EnumSet.of(SmtpAdapter.Flags.NONE));
    }
    catch (SmtpAdapter.MailException | AccessException | IOException e) {
      throw new NotificationException("The notification could not be sent", e);
    }
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public static class Options {
    public static final ZoneId DEFAULT_TIMEZONE = ZoneOffset.UTC;

    private final ZoneId timeZone;

    public Options(ZoneId timeZone) {
      Preconditions.checkNotNull(timeZone);
      this.timeZone = timeZone;
    }
  }
}
