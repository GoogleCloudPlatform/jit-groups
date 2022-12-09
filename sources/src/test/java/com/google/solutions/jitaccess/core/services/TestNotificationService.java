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

import com.google.solutions.jitaccess.core.adapters.SmtpAdapter;
import com.google.solutions.jitaccess.core.data.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestNotificationService {
  private static class TestNotification extends NotificationService.Notification {
    protected TestNotification(
      UserId recipient,
      String subject,
      Map<String, Object> properties
    ) {
      super(
        properties
          .entrySet()
          .stream()
          .map(e -> String.format("%s={{%s}}\n", e.getKey(), e.getKey()))
          .collect(Collectors.joining()),
        List.of(recipient),
        List.of(),
        subject);
      this.properties.putAll(properties);
    }
  }

  // -------------------------------------------------------------------------
  // sendNotification.
  // -------------------------------------------------------------------------

  //@Test
  public void whenSmtpConfigured_ThenSendNotificationSendsMail() throws Exception {
    String senderAddress = "...";
    String recipient = "...";
    String username = "...";
    String password = "...";

    var options = new SmtpAdapter.Options(
      "smtp.mailgun.org",
      587,
      "JIT Access Test",
      senderAddress,
      true,
      Map.of());
    options.setSmtpCredentials(username, password);

    var mailAdapter = new SmtpAdapter(options);
    var service = new NotificationService.MailNotificationService(
      mailAdapter,
      new NotificationService.Options(NotificationService.Options.DEFAULT_TIMEZONE));

    var properties = new HashMap<String, Object>();
    properties.put("TEST", "test-value");

    var notification = new TestNotification(
      new UserId(recipient),
      "Test email",
      properties);

    service.sendNotification(notification);
  }

  @Test
  public void sendNotificationSendsMail() throws Exception {
    var mailAdapter = Mockito.mock(SmtpAdapter.class);
    var service = new NotificationService.MailNotificationService(
      mailAdapter,
      new NotificationService.Options(NotificationService.Options.DEFAULT_TIMEZONE));

    var to = new UserId("user@example.com");
    service.sendNotification(new TestNotification(
      to,
      "Test email",
      new HashMap<String, Object>()));

    verify(mailAdapter, times(1)).sendMail(
      eq(List.of(to)),
      eq(List.of()),
      eq("Test email"),
      anyString(),
      eq(EnumSet.of(SmtpAdapter.Flags.NONE)));
  }

  // -------------------------------------------------------------------------
  // format.
  // -------------------------------------------------------------------------

  @Test
  public void whenPropertiesContainHtmlTags_ThenFormatEscapesTags() throws Exception {
    var properties = new HashMap<String, Object>();
    properties.put("TEST-1", "<value1/>");
    properties.put("TEST-2", "<value2/>");

    var notification = new TestNotification(
      new UserId("user@example.com"),
      "Test email",
      properties);

    assertEquals(
      "TEST-1=&lt;value1/&gt;\nTEST-2=&lt;value2/&gt;\n",
      notification.formatMessage(NotificationService.Options.DEFAULT_TIMEZONE));
  }

  @Test
  public void whenPropertiesContainDates_ThenFormatAppliesTimezone() throws Exception {
    var properties = new HashMap<String, Object>();
    properties.put("TEST-1", Instant.ofEpochSecond(86400));

    var notification = new TestNotification(
      new UserId("user@example.com"),
      "Test email",
      properties);

    assertEquals(
      "TEST-1=Fri, 2 Jan 1970 10:00:00 +1000",
      notification.formatMessage(ZoneId.of("Australia/Melbourne")).trim());
  }
}
