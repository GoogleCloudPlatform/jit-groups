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

import com.google.common.collect.Streams;
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
import java.util.function.Function;
import java.util.stream.Stream;

class ApplicationConfiguration extends AbstractConfiguration {
  /**
   * Prefix for environment service accounts.
   */
  static final @NotNull String ENVIRONMENT_SERVICE_ACCOUNT_PREFIX = "jit-";

  /**
   * Cloud Identity/Workspace customer ID.
   */
  final @NotNull String customerId;

  /**
   * Domain to use for JIT groups. This can be the primary
   * or a secondary domain of the account identified
   * by @see customerId.
   */
  final @NotNull String groupsDomain;

  /**
   * list of environments.
   */
  final @NotNull Collection<String> environments;

  /**
   * Zone to apply to dates when sending notifications.
   */
  final @NotNull ZoneId notificationTimeZone;

  /**
   * Timeout for proposals.
   */
  final @NotNull Duration proposalTimeout;

  /**
   * Timeout for environment cache.
   */
  final @NotNull Duration environmentCacheTimeout;

  /**
   * CEL expression for mapping userIDs to email addresses.
   */
  final @NotNull Optional<String> smtpAddressMapping;

  /**
   * SMTP server for sending notifications.
   */
  final @NotNull String smtpHost;

  /**
   * SMTP port for sending notifications.
   */
  final int smtpPort;

  /**
   * Enable StartTLS.
   */
  final @NotNull boolean smtpEnableStartTls;

  /**
   * Human-readable sender name used for notifications.
   */
  final @NotNull String smtpSenderName;

  /**
   * Email address used for notifications.
   */
  final @NotNull Optional<String> smtpSenderAddress;

  /**
   * SMTP username.
   */
  final @NotNull Optional<String> smtpUsername;

  /**
   * SMTP password. For Gmail, this should be an application-specific password.
   */
  final @NotNull Optional<String> smtpPassword;

  /**
   * Path to a SecretManager secret that contains the SMTP password.
   * For Gmail, this should be an application-specific password.
   *
   * The path must be in the format projects/x/secrets/y/versions/z.
   */
  final @NotNull Optional<String> smtpSecret;

  /**
   * Extra JavaMail options.
   */
  final @NotNull Optional<String> smtpExtraOptions;

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


  final @NotNull Setting<String> legacyCatalog;
  final @NotNull Setting<String> legacyScope;
  final @NotNull Setting<Duration> legacyActivationTimeout;
  final @NotNull Setting<String> legacyJustificationPattern;
  final @NotNull Setting<String> legacyJustificationHint;
  final @NotNull Setting<String> legacyProjectsQuery;


  public ApplicationConfiguration(@NotNull Map<String, String> settingsData) {
    super(settingsData);

    //
    // Basic settings.
    //

    this.customerId = readStringSetting(
      "CUSTOMER_ID",
      "RESOURCE_CUSTOMER_ID") // Name used in 1.x
      .orElseThrow(() -> new IllegalStateException(
        "The environment variable 'CUSTOMER_ID' must be set to the customer ID " +
          "of a Cloud Identity or Workspace account"));

    this.groupsDomain = readStringSetting("GROUPS_DOMAIN")
      .orElseThrow(() -> new IllegalStateException(
        "The environment variable 'GROUPS_DOMAIN' must contain a (verified) domain name"));

    this.proposalTimeout = readDurationSetting(
      ChronoUnit.MINUTES,
      "APPROVAL_TIMEOUT",
      "ACTIVATION_REQUEST_TIMEOUT") // Name used in 1.x
      .orElse(Duration.ofHours(1));

    this.environments = readStringSetting("ENVIRONMENTS").stream()
      .flatMap(s -> Arrays.stream(s.split(",")))
      .map(String::trim)
      .filter(s -> !s.isBlank())
      .toList();

    this.environmentCacheTimeout = readDurationSetting(
      ChronoUnit.SECONDS,
      "RESOURCE_CACHE_TIMEOUT")
      .orElse(Duration.ofMinutes(5));

    //
    // SMTP settings.
    //
    this.smtpAddressMapping = readStringSetting("SMTP_ADDRESS_MAPPING");
    this.smtpHost = readStringSetting("SMTP_HOST").orElse("smtp.gmail.com");
    this.smtpPort = readSetting(Integer::parseInt, "SMTP_PORT").orElse(587);
    this.smtpEnableStartTls = readSetting(Boolean::parseBoolean, "SMTP_ENABLE_STARTTLS").orElse(true);
    this.smtpSenderName = readStringSetting("SMTP_SENDER_NAME").orElse("JIT Groups");
    this.smtpSenderAddress = readStringSetting("SMTP_SENDER_ADDRESS");
    this.smtpUsername = readStringSetting("SMTP_USERNAME");
    this.smtpPassword = readStringSetting("SMTP_PASSWORD");
    this.smtpSecret = readStringSetting("SMTP_SECRET");
    this.smtpExtraOptions =readStringSetting("SMTP_OPTIONS");

    //
    // Notification settings.
    //
    this.notificationTimeZone = readSetting(ZoneId::of, "NOTIFICATION_TIMEZONE")
      .orElse(ZoneOffset.UTC);

    //
    // Backend service id (Cloud Run only).
    //
    this.backendServiceId = new StringSetting("IAP_BACKEND_SERVICE_ID", null);
    this.verifyIapAudience = new BooleanSetting("IAP_VERIFY_AUDIENCE", true);


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
    // Legacy settings.
    //
    this.legacyCatalog = new StringSetting("RESOURCE_CATALOG", "AssetInventory");
    this.legacyScope = new StringSetting(
      "RESOURCE_SCOPE",
      null);
    this.legacyActivationTimeout = new DurationSetting(
      "ACTIVATION_TIMEOUT",
      List.of("ELEVATION_DURATION"),
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
    return this.smtpSenderAddress.isPresent();
  }

  public boolean isSmtpAuthenticationConfigured() {
    return this.smtpUsername.isPresent() &&
      (this.smtpPassword.isPresent() || this.smtpSecret.isPresent());
  }

  @NotNull Set<String> requiredOauthScopes() {
    return new HashSet<>(List.of(
      IamCredentialsClient.OAUTH_SCOPE,
      SecretManagerClient.OAUTH_SCOPE,
      CloudIdentityGroupsClient.OAUTH_GROUPS_SCOPE,
      CloudIdentityGroupsClient.OAUTH_SETTINGS_SCOPE));
  }

  public @NotNull Map<String, String> smtpExtraOptionsMap() {
    var map = new HashMap<String, String>();

    if (this.smtpExtraOptions.isPresent()) {
      for (var kvp : this.smtpExtraOptions.get().split(",")) {
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
    private final @NotNull Collection<String> aliases;
    private final @Nullable T defaultValue;

    protected abstract T parse(String value);

    protected Setting(
      @NotNull String key,
      @NotNull Collection<String> aliases,
      @Nullable T defaultValue
    ) {
      this.key = key;
      this.aliases = aliases;
      this.defaultValue = defaultValue;
    }

    public @Nullable String key() {
      return this.key;
    }

    public @Nullable T value() {
      var value = Streams.concat(Stream.of(this.key), this.aliases.stream())
        .map(ApplicationConfiguration.this.settingsData::get)
        .filter(v -> v != null)
        .map(String::trim)
        .filter(v -> !v.isBlank())
        .map(this::parse)
        .findFirst();

      if (value.isPresent()) {
        return value.get();
      }
      else if (this.defaultValue != null) {
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

    public boolean isDefault() {
      return Objects.equals(value(), this.defaultValue);
    }
  }

  private class StringSetting extends Setting<String> {
    public StringSetting(@NotNull String key, @Nullable String defaultValue) {
      super(key, List.of(), defaultValue);
    }

    public StringSetting(
      @NotNull String key,
      @NotNull Collection<String> aliases,
      @Nullable String defaultValue
    ) {
      super(key, aliases, defaultValue);
    }

    @Override
    protected String parse(String value) {
      return value;
    }
  }

  private class IntSetting extends Setting<Integer> {
    public IntSetting(String key, Integer defaultValue) {
      super(key, List.of(), defaultValue);
    }

    @Override
    protected @NotNull Integer parse(@NotNull String value) {
      return Integer.parseInt(value);
    }
  }

  private class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(@NotNull String key, @Nullable Boolean defaultValue) {
      super(key, List.of(), defaultValue);
    }

    @Override
    protected @NotNull Boolean parse(String value) {
      return Boolean.parseBoolean(value);
    }
  }

  private class DurationSetting extends Setting<Duration> {
    private final @NotNull ChronoUnit unit;

    public DurationSetting(
      @NotNull String key,
      @NotNull Collection<String> aliases,
      @NotNull ChronoUnit unit,
      @Nullable Duration defaultValue) {
      super(key, aliases, defaultValue);
      this.unit = unit;
    }

    public DurationSetting(@NotNull String key, ChronoUnit unit, Duration defaultValue) {
      this(key, List.of(), unit, defaultValue);
    }

    @Override
    protected Duration parse(@NotNull String value) {
      return Duration.of(Integer.parseInt(value), this.unit);
    }
  }

  private class ZoneIdSetting extends Setting<ZoneId> {
    public ZoneIdSetting(@NotNull String key) {
      super(key, List.of(), ZoneOffset.UTC);
    }

    @Override
    protected @NotNull ZoneId parse(@NotNull String value) {
      return ZoneId.of(value);
    }
  }
}
