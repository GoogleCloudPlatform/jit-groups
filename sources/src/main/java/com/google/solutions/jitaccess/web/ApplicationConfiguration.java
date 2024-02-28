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

package com.google.solutions.jitaccess.web;

import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.apis.clients.IamCredentialsClient;
import com.google.solutions.jitaccess.apis.clients.SecretManagerClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

class ApplicationConfiguration {
  /**
   * Pattern for environment service accounts.
   */
  static final @NotNull Pattern ENVIRONMENT_SERVICE_ACCOUNT_PATTERN =
    Pattern.compile("^jit-(.+)@(.+).iam.gserviceaccount.com$");

  /**
   * Raw settings data, typically sourced from the environment block.
   */
  private final @NotNull Map<String, String> settingsData;

  /**
   * Cloud Identity/Workspace customer ID.
   */
  final @NotNull Setting<String> customerId;

  /**
   * Domain to use for JIT groups. This can be the primary
   * or a secondary domain of the account identified
   * by @see customerId.
   */
  final @NotNull Setting<String> groupsDomain;

  /**
   * Zone to apply to dates when sending notifications.
   */
  final @NotNull Setting<ZoneId> notificationTimeZone;

  /**
   * CEL expression for mapping userIDs to email addresses.
   */
  final @NotNull Setting<String> smtpAddressMapping;

  /**
   * SMTP server for sending notifications.
   */
  final @NotNull Setting<String> smtpHost;

  /**
   * SMTP port for sending notifications.
   */
  final @NotNull Setting<Integer> smtpPort;

  /**
   * Enable StartTLS.
   */
  final @NotNull Setting<Boolean> smtpEnableStartTls;

  /**
   * Human-readable sender name used for notifications.
   */
  final @NotNull Setting<String> smtpSenderName;

  /**
   * Email address used for notifications.
   */
  final @NotNull Setting<String> smtpSenderAddress;

  /**
   * SMTP username.
   */
  final @NotNull Setting<String> smtpUsername;

  /**
   * SMTP password. For Gmail, this should be an application-specific password.
   */
  final @NotNull Setting<String> smtpPassword;

  /**
   * Path to a SecretManager secret that contains the SMTP password.
   * For Gmail, this should be an application-specific password.
   *
   * The path must be in the format projects/x/secrets/y/versions/z.
   */
  final @NotNull Setting<String> smtpSecret;

  /**
   * Extra JavaMail options.
   */
  final @NotNull Setting<String> smtpExtraOptions;

  /**
   * Backend Service Id for token validation
   */
  final @NotNull Setting<String> backendServiceId;

  /**
   * Check audience of IAP assertions.
   */
  final @NotNull Setting<Boolean> verifyIapAudience;

  /**
   * Connect timeout for HTTP requests to backends.
   */
  final @NotNull Setting<Duration> backendConnectTimeout;

  /**
   * Read timeout for HTTP requests to backends.
   */
  final @NotNull Setting<Duration> backendReadTimeout;

  /**
   * Write timeout for HTTP requests to backends.
   */
  final @NotNull Setting<Duration> backendWriteTimeout;

  /**
   * Timeout for environment cache.
   */
  final @NotNull Setting<Duration> environmentCacheTimeout;

  /**
   * Comma-separated list of environments.
   */
  final @NotNull Setting<String> environments;

  /**
   * Timeout for proposals.
   */
  final @NotNull Setting<Duration> proposalTimeout;

  final @NotNull Setting<String> legacyCatalog;
  final @NotNull Setting<String> legacyScope;
  final @NotNull Setting<Duration> legacyActivationTimeout;
  final @NotNull Setting<String> legacyJustificationPattern;
  final @NotNull Setting<String> legacyJustificationHint;
  final @NotNull Setting<String> legacyProjectsQuery;

  public ApplicationConfiguration(@NotNull Map<String, String> settingsData) { // TODO: test
    this.settingsData = settingsData;

    this.customerId = new StringSetting(
      "RESOURCE_CUSTOMER_ID",
       null);
    this.groupsDomain = new StringSetting(
      "RESOURCE_DOMAIN",
      null);

    //
    // Backend service id (Cloud Run only).
    //
    this.backendServiceId = new StringSetting("IAP_BACKEND_SERVICE_ID", null);
    this.verifyIapAudience = new BooleanSetting("IAP_VERIFY_AUDIENCE", true);

    //
    // Notification settings.
    //
    this.notificationTimeZone = new ZoneIdSetting("NOTIFICATION_TIMEZONE");

    this.proposalTimeout = new DurationSetting(
      "APPROVAL_TIMEOUT",
      ChronoUnit.MINUTES,
      Duration.ofHours(1));

    //
    // SMTP settings.
    //
    this.smtpAddressMapping = new StringSetting("SMTP_ADDRESS_MAPPING", "");
    this.smtpHost = new StringSetting("SMTP_HOST", "smtp.gmail.com");
    this.smtpPort = new IntSetting("SMTP_PORT", 587);
    this.smtpEnableStartTls = new BooleanSetting("SMTP_ENABLE_STARTTLS", true);
    this.smtpSenderName = new StringSetting("SMTP_SENDER_NAME", "JIT Access");
    this.smtpSenderAddress = new StringSetting("SMTP_SENDER_ADDRESS", null);
    this.smtpUsername = new StringSetting("SMTP_USERNAME", null);
    this.smtpPassword = new StringSetting("SMTP_PASSWORD", null);
    this.smtpSecret = new StringSetting("SMTP_SECRET", null);
    this.smtpExtraOptions = new StringSetting("SMTP_OPTIONS", null);

    //
    // Backend settings.
    //
    this.backendConnectTimeout = new DurationSetting(
     "BACKEND_CONNECT_TIMEOUT",
      ChronoUnit.SECONDS,
      Duration.ofSeconds(5));
    this.backendReadTimeout = new DurationSetting(
     "BACKEND_READ_TIMEOUT",
      ChronoUnit.SECONDS,
      Duration.ofSeconds(20));
    this.backendWriteTimeout = new DurationSetting(
     "BACKEND_WRITE_TIMEOUT",
      ChronoUnit.SECONDS,
      Duration.ofSeconds(5));

    //
    // Environment settings.
    //
    this.environmentCacheTimeout = new DurationSetting(
      "RESOURCE_CACHE_TIMEOUT",
      ChronoUnit.SECONDS,
      Duration.ofMinutes(5));
    this.environments = new StringSetting("RESOURCE_ENVIRONMENTS", "");


    //
    // Legacy settings.
    //
    this.legacyCatalog = new StringSetting("RESOURCE_CATALOG", "AssetInventory");
    this.legacyScope = new StringSetting(
      "RESOURCE_SCOPE",
      String.format("projects/%s", this.settingsData.get("GOOGLE_CLOUD_PROJECT")));
    this.legacyActivationTimeout = new DurationSetting(
      "ACTIVATION_TIMEOUT",
      ChronoUnit.MINUTES,
      Duration.ofHours(2));
    this.legacyJustificationPattern = new StringSetting(
      "JUSTIFICATION_PATTERN",
      ".*");
    this.legacyJustificationHint = new StringSetting(
      "JUSTIFICATION_HINT",
      "Bug or case number");
    this.legacyProjectsQuery = new StringSetting(
      "AVAILABLE_PROJECTS_QUERY",
      "state:ACTIVE");
  }

  boolean isSmtpConfigured() {
    var requiredSettings = List.of(smtpHost, smtpPort, smtpSenderName, smtpSenderAddress);
    return requiredSettings.stream().allMatch(s -> s.isValid());
  }

  public boolean isSmtpAuthenticationConfigured() {
    return this.smtpUsername.isValid() &&
      (this.smtpPassword.isValid() || this.smtpSecret.isValid());
  }

  @NotNull Set<String> requiredOauthScopes() {
    return new HashSet<>(List.of(
      IamCredentialsClient.OAUTH_SCOPE,
      SecretManagerClient.OAUTH_SCOPE,
      CloudIdentityGroupsClient.OAUTH_GROUPS_SCOPE,
      CloudIdentityGroupsClient.OAUTH_SETTINGS_SCOPE));
  }

  @NotNull Collection<String> environments() {
    return Arrays.stream(this.environments.value().split(","))
      .map(String::trim)
      .filter(s -> !s.isBlank())
      .toList();
  }

  public @NotNull Map<String, String> smtpExtraOptionsMap() {
    var map = new HashMap<String, String>();

    if (this.smtpExtraOptions.isValid()) {
      for (var kvp : this.smtpExtraOptions.value().split(",")) {
        var parts = kvp.split("=");
        if (parts.length == 2) {
          map.put(parts[0].trim(), parts[1].trim());
        }
      }
    }

    return map;
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public abstract class Setting<T> {
    private final @NotNull String key;
    private final @Nullable T defaultValue;

    protected abstract T parse(String value);

    protected Setting(@NotNull String key, @Nullable T defaultValue) {
      this.key = key;
      this.defaultValue = defaultValue;
    }

    public @Nullable String key() {
      return this.key;
    }

    public @Nullable T value() {
      var value = ApplicationConfiguration.this.settingsData.get(key);
      if (value != null) {
        value = value.trim();
        if (!value.isEmpty()) {
          return parse(value);
        }
      }

      if (this.defaultValue != null) {
        return this.defaultValue;
      }
      else {
        throw new IllegalStateException("No value provided for " + this.key);
      }
    }

    public boolean isValid() {
      try {
        value();
        return true;
      }
      catch (Exception ignored) {
        return false;
      }
    }
  }

  private class StringSetting extends Setting<String> {
    public StringSetting(@NotNull String key, @Nullable String defaultValue) {
      super(key, defaultValue);
    }

    @Override
    protected String parse(String value) {
      return value;
    }
  }

  private class IntSetting extends Setting<Integer> {
    public IntSetting(String key, Integer defaultValue) {
      super(key, defaultValue);
    }

    @Override
    protected @NotNull Integer parse(@NotNull String value) {
      return Integer.parseInt(value);
    }
  }

  private class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(@NotNull String key, @Nullable Boolean defaultValue) {
      super(key, defaultValue);
    }

    @Override
    protected @NotNull Boolean parse(String value) {
      return Boolean.parseBoolean(value);
    }
  }

  private class DurationSetting extends Setting<Duration> {
    private final ChronoUnit unit;
    public DurationSetting(@NotNull String key, ChronoUnit unit, Duration defaultValue) {
      super(key, defaultValue);
      this.unit = unit;
    }

    @Override
    protected Duration parse(@NotNull String value) {
      return Duration.of(Integer.parseInt(value), this.unit);
    }
  }

  private class ZoneIdSetting extends Setting<ZoneId> {
    public ZoneIdSetting(@NotNull String key) {
      super(key, ZoneOffset.UTC);
    }

    @Override
    protected @NotNull ZoneId parse(@NotNull String value) {
      return ZoneId.of(value);
    }
  }
}
