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

import com.google.solutions.jitaccess.apis.CustomerId;
import com.google.solutions.jitaccess.apis.Domain;
import com.google.solutions.jitaccess.apis.OrganizationId;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.apis.clients.IamCredentialsClient;
import com.google.solutions.jitaccess.apis.clients.SecretManagerClient;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class ApplicationConfiguration extends AbstractConfiguration {
  /**
   * Prefix for environment service accounts.
   */
  static final @NotNull String ENVIRONMENT_SERVICE_ACCOUNT_PREFIX = "jit-";

  /**
   * Cloud Identity/Workspace customer ID.
   */
  final @NotNull CustomerId customerId;

  /**
   * Cloud Identity/Workspace primary domain.
   */
  final @NotNull Domain primaryDomain;


  /**
   * Google Cloud organization ID.
   */
  final @NotNull OrganizationId organizationId;

  /**
   * Domain to use for JIT groups. This can be the primary
   * or a secondary domain of the account identified
   * by @see customerId.
   */
  final @NotNull Domain groupsDomain;

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
  final boolean smtpEnableStartTls;

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
   * <p>
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
  final @NotNull Optional<String> backendServiceId;

  /**
   * Check audience of IAP assertions.
   */
  final boolean verifyIapAudience;

  /**
   * Connect timeout for HTTP requests to backends.
   */
  final @NotNull Duration backendConnectTimeout;

  /**
   * Read timeout for HTTP requests to backends.
   */
  final @NotNull Duration backendReadTimeout;

  /**
   * Write timeout for HTTP requests to backends.
   */
  final @NotNull Duration backendWriteTimeout;

  final @NotNull String legacyCatalog;
  final @NotNull Optional<String> legacyScope;
  final @NotNull Duration legacyActivationTimeout;
  final @NotNull String legacyJustificationPattern;
  final @NotNull String legacyJustificationHint;
  final @NotNull String legacyProjectsQuery;

  public ApplicationConfiguration(@NotNull Map<String, String> settingsData) {
    super(settingsData);

    //
    // Read required settings.
    //
    // NB. CustomerIDs, primary domains, organization IDs can be translated
    //     into each other by using the organization.search API, but this
    //     API requires the `resourcemanager.organizations.get` permission.
    //
    //     Service accounts don't have this permission by default, and granting
    //     the corresponding `Organization Viewer` role requires changing the
    //     organzization's IAM policy, which is a lot to ask for.
    //
    //     Therefore, we require the configuration to contain all 3 pieces of
    //     information, even if it's somewhat redundant.
    //
    this.customerId = readStringSetting(
      "CUSTOMER_ID",
      "RESOURCE_CUSTOMER_ID") // Name used in 1.x
      .map(CustomerId::new)
      .orElseThrow(() -> new IllegalStateException(
        "The environment variable 'CUSTOMER_ID' must contain the customer ID " +
          "of a Cloud Identity or Workspace account, see" +
          "https://support.google.com/a/answer/10070793"));
    this.primaryDomain = readStringSetting("PRIMARY_DOMAIN")
      .map(d -> new Domain(d, Domain.Type.PRIMARY))
      .orElseThrow(() -> new IllegalStateException(
        "The environment variable 'PRIMARY_DOMAIN' must contain the primary domain name " +
          "of a Cloud Identity/Workspace account, see https://support.google.com/a/answer/182080"));
    this.organizationId = readStringSetting("ORGANIZATION_ID")
      .map(OrganizationId::new)
      .orElseThrow(() -> new IllegalStateException(
        "The environment variable 'ORGANIZATION_ID' must contain the organization ID of " +
          "a Google Cloud organization, see " +
          "https://cloud.google.com/resource-manager/docs/creating-managing-organization"));

    //
    // Read optional settings.
    //
    this.groupsDomain = readStringSetting("GROUPS_DOMAIN")
      .map(d -> new Domain(d, d.equalsIgnoreCase(this.primaryDomain.name())
        ? Domain.Type.PRIMARY
        : Domain.Type.SECONDARY))
      .orElse(this.primaryDomain);
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
    this.smtpHost = readStringSetting("SMTP_HOST")
      .orElse("smtp.gmail.com");
    this.smtpPort = readSetting(Integer::parseInt, "SMTP_PORT")
      .orElse(587);
    this.smtpEnableStartTls = readSetting(Boolean::parseBoolean, "SMTP_ENABLE_STARTTLS")
      .orElse(true);
    this.smtpSenderName = readStringSetting("SMTP_SENDER_NAME")
      .orElse("JIT Groups");
    this.smtpSenderAddress = readStringSetting("SMTP_SENDER_ADDRESS");
    this.smtpUsername = readStringSetting("SMTP_USERNAME");
    this.smtpPassword = readStringSetting("SMTP_PASSWORD");
    this.smtpSecret = readStringSetting("SMTP_SECRET");
    this.smtpExtraOptions = readStringSetting("SMTP_OPTIONS");

    //
    // Notification settings.
    //
    this.notificationTimeZone = readSetting(ZoneId::of, "NOTIFICATION_TIMEZONE")
      .orElse(ZoneOffset.UTC);

    //
    // Backend service id (Cloud Run only).
    //
    this.backendServiceId = readStringSetting("IAP_BACKEND_SERVICE_ID");
    this.verifyIapAudience = readSetting(Boolean::parseBoolean, "IAP_VERIFY_AUDIENCE")
      .orElse(true);

    //
    // Backend settings.
    //
    this.backendConnectTimeout = readDurationSetting(ChronoUnit.SECONDS, "BACKEND_CONNECT_TIMEOUT")
      .orElse(Duration.ofSeconds(5));
    this.backendReadTimeout = readDurationSetting(ChronoUnit.SECONDS, "BACKEND_READ_TIMEOUT")
      .orElse(Duration.ofSeconds(20));
    this.backendWriteTimeout = readDurationSetting(ChronoUnit.SECONDS, "BACKEND_WRITE_TIMEOUT")
      .orElse(Duration.ofSeconds(5));

    //
    // Legacy settings.
    //
    this.legacyCatalog = readStringSetting("RESOURCE_CATALOG").orElse("AssetInventory");
    this.legacyScope = readStringSetting("RESOURCE_SCOPE");
    this.legacyActivationTimeout = readDurationSetting(
      ChronoUnit.MINUTES,
      "ACTIVATION_TIMEOUT",
      "ELEVATION_DURATION")
      .orElse(Duration.ofHours(2));
    this.legacyJustificationPattern = readStringSetting("JUSTIFICATION_PATTERN")
      .orElse(".*");
    this.legacyJustificationHint = readStringSetting("JUSTIFICATION_HINT")
      .orElse("Bug or case number");
    this.legacyProjectsQuery = readStringSetting("AVAILABLE_PROJECTS_QUERY")
      .orElse("state:ACTIVE");
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
}
