//
// Copyright 2023 Google LLC
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

import com.google.common.html.HtmlEscapers;
import com.google.solutions.jitaccess.core.data.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestNotificationTemplate {
  private static class TestNotification extends NotificationService.Notification {
    private final String templateId;

    protected TestNotification(
      UserId recipient,
      String subject,
      Map<String, Object> properties,
      String templateId
    ) {
      super(
        List.of(recipient),
        List.of(),
        subject);
      this.properties.putAll(properties);
      this.templateId = templateId;
    }

    @Override
    public String getTemplateId() {
      return this.templateId;
    }
  }

  // -------------------------------------------------------------------------
  // NotificationTemplate.
  // -------------------------------------------------------------------------

  @Test
  public void whenPropertiesContainHtmlTags_ThenFormatEscapesTags() {
    var properties = new HashMap<String, Object>();
    properties.put("TEST-1", "<value1/>");
    properties.put("TEST-2", "<value2/>");

    var notification = new TestNotification(
      new UserId("user@example.com"),
      "Test email",
      properties,
      "ignored-templateid");

    var template = new NotificationService.NotificationTemplate(
      notification.properties
        .entrySet()
        .stream()
        .map(e -> String.format("%s={{%s}}\n", e.getKey(), e.getKey()))
        .collect(Collectors.joining()),
      NotificationService.Options.DEFAULT_TIMEZONE,
      HtmlEscapers.htmlEscaper());

    assertEquals(
      "TEST-1=&lt;value1/&gt;\nTEST-2=&lt;value2/&gt;\n",
      template.format(notification));
  }

  @Test
  public void whenPropertiesContainDates_ThenFormatAppliesTimezone() {
    var properties = new HashMap<String, Object>();
    properties.put("TEST-1", Instant.ofEpochSecond(86400));

    var notification = new TestNotification(
      new UserId("user@example.com"),
      "Test email",
      properties,
      "ignored-templateid");

    var template = new NotificationService.NotificationTemplate(
      notification.properties
        .entrySet()
        .stream()
        .map(e -> String.format("%s={{%s}}\n", e.getKey(), e.getKey()))
        .collect(Collectors.joining()),
      ZoneId.of("Australia/Melbourne"),
      HtmlEscapers.htmlEscaper());

    assertEquals(
      "TEST-1=Fri, 2 Jan 1970 10:00:00 +1000",
      template.format(notification).trim());
  }
}
