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

import com.google.solutions.jitaccess.core.adapters.MailAdapter;
import com.google.solutions.jitaccess.core.data.ProjectRole;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestNotificationService {
  private static class TestNotification extends NotificationService.Notification {
    protected TestNotification(
      UserId recipient,
      String subject,
      Map<String, String> properties
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

    var options = new MailAdapter.Options(
      "smtp.mailgun.org",
      587,
      "JIT Access Test",
      senderAddress);
    options.setSmtpCredentials(username, password);

    var adapter = new MailAdapter(options);
    var service = new NotificationService(
      adapter,
      new NotificationService.Options(true));

    var properties = new HashMap<String, String>();
    properties.put("TEST", "test-value");

    var notification = new TestNotification(
      new UserId(recipient),
      "Test email",
      properties);

    service.sendNotification(notification);
  }

  @Test
  public void whenEmailEnabled_ThenSendNotificationSendsMail() throws Exception {
    var mailAdapter = Mockito.mock(MailAdapter.class);
    var service = new NotificationService(
      mailAdapter,
      new NotificationService.Options(true));

    var to = new UserId("user@example.com");
    service.sendNotification(new TestNotification(
      to,
      "Test email",
      new HashMap<String, String>()));

    verify(mailAdapter, times(1)).sendMail(
      eq(List.of(to)),
      eq(List.of()),
      eq("Test email"),
      anyString(),
      eq(EnumSet.of(MailAdapter.Flags.NONE)));
  }

  @Test
  public void whenEmailDisabled_ThenSendNotificationDoesNothing() throws Exception {
    var mailAdapter = Mockito.mock(MailAdapter.class);
    var service = new NotificationService(
      mailAdapter,
      new NotificationService.Options(false));

    service.sendNotification(new TestNotification(
      new UserId("user@example.com"),
      "Test email",
      new HashMap<String, String>()));

    verify(mailAdapter, times(0)).sendMail(
      anyList(),
      anyList(),
      anyString(),
      anyString(),
      eq(EnumSet.of(MailAdapter.Flags.NONE)));
  }

  // -------------------------------------------------------------------------
  // format.
  // -------------------------------------------------------------------------

  @Test
  public void whenPropertiesContainHtmlTags_ThenFormatEscapesTags() throws Exception {
    var properties = new HashMap<String, String>();
    properties.put("TEST-1", "<value1/>");
    properties.put("TEST-2", "<value2/>");

    var notification = new TestNotification(
      new UserId("user@example.com"),
      "Test email",
      properties);

    assertEquals(
      "TEST-1=&lt;value1/&gt;\nTEST-2=&lt;value2/&gt;\n",
      notification.formatMessage());
  }
}
