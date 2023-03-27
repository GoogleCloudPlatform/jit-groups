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

package com.google.solutions.jitaccess.web;

import com.google.solutions.jitaccess.core.services.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.zone.ZoneRulesException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestRuntimeConfiguration {
  @Test
  public void whenNotSet_ThenScopeSetToDefault() {
    var settings = Map.of("GOOGLE_CLOUD_PROJECT", "project-1");
    var configuration = new RuntimeConfiguration(settings);

    assertEquals("projects/project-1", configuration.scope.getValue());
  }

  // -------------------------------------------------------------------------
  // Scope settings.
  // -------------------------------------------------------------------------

  @Test
  public void whenNotSet_ThenActivationTimeoutSetToDefault() {
    var configuration = new RuntimeConfiguration(Map.of());

    assertEquals(Duration.ofHours(2), configuration.activationTimeout.getValue());
  }

  @Test
  public void whenSet_ThenScopeReturnsSetting() {
    var settings = Map.of("RESOURCE_SCOPE", "folders/123");
    var configuration = new RuntimeConfiguration(settings);

    assertEquals("folders/123", configuration.scope.getValue());
  }

  // -------------------------------------------------------------------------
  // Activation settings.
  // -------------------------------------------------------------------------

  @Test
  public void whenNotSet_ThenActivationRequestTimeoutSetToDefault() {
    var configuration = new RuntimeConfiguration(Map.of());

    assertEquals(Duration.ofHours(1), configuration.activationRequestTimeout.getValue());
  }

  @Test
  public void whenNotSet_ThenJustificationPatternSetToDefault() {
    var configuration = new RuntimeConfiguration(Map.of());

    assertNotNull(configuration.justificationPattern.getValue());
  }

  @Test
  public void whenNotSet_ThenJustificationHintSetToDefault() {
    var configuration = new RuntimeConfiguration(Map.of());

    assertNotNull(configuration.justificationHint.getValue());
  }

  @Test
  public void whenSet_ThenActivationTimeoutReturnsSetting() {
    var settings = Map.of("ELEVATION_DURATION", "30");
    var configuration = new RuntimeConfiguration(settings);

    assertEquals(Duration.ofMinutes(30), configuration.activationTimeout.getValue());
  }

  // -------------------------------------------------------------------------
  // Notification settings.
  // -------------------------------------------------------------------------

  @Test
  public void whenSet_ThenTimeZoneForNotificationsIsUtc() {
    var configuration = new RuntimeConfiguration(Map.of());

    assertEquals(
      NotificationService.Options.DEFAULT_TIMEZONE,
      configuration.timeZoneForNotifications.getValue());
  }

  @Test
  public void whenInvalid_ThenTimeZoneForNotificationsIsInvalid() {
    var settings = Map.of("NOTIFICATION_TIMEZONE", "junk");
    var configuration = new RuntimeConfiguration(settings);

    assertFalse(configuration.timeZoneForNotifications.isValid());
    assertThrows(ZoneRulesException.class,
      () -> configuration.timeZoneForNotifications.getValue());
  }

  @Test
  public void whenSet_ThenTimeZoneForNotificationsReturnsSetting() {
    var settings = Map.of("NOTIFICATION_TIMEZONE", "Australia/Melbourne");
    var configuration = new RuntimeConfiguration(settings);

    assertNotEquals(
      NotificationService.Options.DEFAULT_TIMEZONE,
      configuration.timeZoneForNotifications.getValue());
  }

  // -------------------------------------------------------------------------
  // SMTP settings.
  // -------------------------------------------------------------------------

  @Test
  public void whenNotSet_ThenSmtpSettingsSetToDefault() {
    var configuration = new RuntimeConfiguration(Map.of());

    assertEquals("smtp.gmail.com", configuration.smtpHost.getValue());
    assertEquals(587, configuration.smtpPort.getValue());
    assertTrue(configuration.smtpEnableStartTls.getValue());
    assertEquals("JIT Access", configuration.smtpSenderName.getValue());
    assertFalse(configuration.smtpSenderAddress.isValid());
    assertFalse(configuration.smtpUsername.isValid());
    assertFalse(configuration.smtpPassword.isValid());
  }

  @Test
  public void whenSet_ThenSmtpSettingsReturnSettings() {
    var configuration = new RuntimeConfiguration(Map.of(
      "SMTP_HOST", "mail.example.com ",
      "SMTP_PORT", " 25 ",
      "SMTP_ENABLE_STARTTLS", " False ",
      "SMTP_SENDER_NAME", "Sender",
      "SMTP_SENDER_ADDRESS", "sender@example.com",
      "SMTP_USERNAME", "user",
      "SMTP_PASSWORD", "password"
    ));

    assertEquals("mail.example.com", configuration.smtpHost.getValue());
    assertEquals(25, configuration.smtpPort.getValue());
    assertFalse(configuration.smtpEnableStartTls.getValue());
    assertEquals("Sender", configuration.smtpSenderName.getValue());
    assertEquals("sender@example.com", configuration.smtpSenderAddress.getValue());
    assertEquals("user", configuration.smtpUsername.getValue());
    assertEquals("password", configuration.smtpPassword.getValue());
  }

  @Test
  public void whenSmtpExtraOptionsEmpty_ThenGetSmtpExtraOptionsRetunsMap() {
    var settings = Map.of("SMTP_OPTIONS", "");
    var configuration = new RuntimeConfiguration(settings);

    var extraOptions = configuration.getSmtpExtraOptionsMap();
    assertNotNull(extraOptions);
    assertEquals(0, extraOptions.size());
  }

  @Test
  public void whenSmtpExtraOptionsContainsPairs_ThenGetSmtpExtraOptionsRetunsMap() {
    var settings = Map.of("SMTP_OPTIONS", " , ONE = one, TWO=two,THREE,FOUR=,,   ");
    var configuration = new RuntimeConfiguration(settings);

    var extraOptions = configuration.getSmtpExtraOptionsMap();
    assertNotNull(extraOptions);
    assertEquals(2, extraOptions.size());
    assertEquals("one", extraOptions.get("ONE"));
    assertEquals("two", extraOptions.get("TWO"));
  }

  @Test
  public void whenSmtpPasswordAndSecretNotSet_ThenIsSmtpAuthenticationConfiguredIsFalse() {
    var settings = Map.of("SMTP_USERNAME=user", "SMTP_SECRET=");
    var configuration = new RuntimeConfiguration(settings);
    assertFalse(configuration.isSmtpAuthenticationConfigured());
  }

  @Test
  public void whenSmtpUserAndPasswordSet_ThenIsSmtpAuthenticationConfiguredIsTrue() {
    var settings = Map.of("SMTP_USERNAME=user", "SMTP_PASSWORD= pwd");
    var configuration = new RuntimeConfiguration(settings);
    assertFalse(configuration.isSmtpAuthenticationConfigured());
  }

  @Test
  public void whenSmtpUserAndSecretSet_ThenIsSmtpAuthenticationConfiguredIsTrue() {
    var settings = Map.of("SMTP_USERNAME=user", "SMTP_SECRET=path");
    var configuration = new RuntimeConfiguration(settings);
    assertFalse(configuration.isSmtpAuthenticationConfigured());
  }
}
