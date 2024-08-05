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

package com.google.solutions.jitaccess.apis.clients;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.catalog.auth.EmailAddress;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * Adapter for sending email over SMTP.
 */
public class SmtpClient extends MailClient {
  private final @NotNull SecretManagerClient secretManagerClient;
  private final @NotNull Options options;

  public SmtpClient(
    @NotNull SecretManagerClient secretManagerClient,
    @NotNull Options options
  ) {
    Preconditions.checkNotNull(secretManagerClient, "secretManagerAdapter");
    Preconditions.checkNotNull(options, "options");

    this.secretManagerClient = secretManagerClient;
    this.options = options;
  }

  @Override
  protected InternetAddress senderAddress() throws MailException, IOException {
    return new InternetAddress(
      options.senderAddress.value(),
      options.senderName);
  }

  @Override
  protected Session createSession() throws MailException {
    PasswordAuthentication authentication;
    try {
      authentication = this.options.createPasswordAuthentication(this.secretManagerClient);
    }
    catch (Exception e) {
      throw new MailException("Looking up SMTP credentials failed", e);
    }

    return Session.getDefaultInstance(
      this.options.smtpProperties,
      new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          return authentication;
        }
      });
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------


  public static class Options {
    private @Nullable PasswordAuthentication cachedAuthentication = null;
    private final @NotNull String senderName;
    private final @NotNull EmailAddress senderAddress;
    private final @NotNull Properties smtpProperties;
    private String smtpUsername;
    private String smtpPassword;
    private String smtpSecretPath;

    public Options(
      @NotNull String smtpHost,
      int smtpPort,
      @NotNull String senderName,
      @NotNull EmailAddress senderAddress,
      boolean enableStartTls,
      @Nullable Map<String, String> extraOptions
    ) {
      Preconditions.checkNotNull(smtpHost, "smtpHost");
      Preconditions.checkNotNull(senderName, "senderName");
      Preconditions.checkNotNull(senderAddress, "senderAddress");

      Preconditions.checkArgument(smtpPort != 25, "SMTP on port 25 is not allowed on Google Cloud");

      this.senderName = senderName;
      this.senderAddress = senderAddress;

      this.smtpProperties = new Properties();
      this.smtpProperties.put("mail.smtp.host", smtpHost);
      this.smtpProperties.put("mail.smtp.port", String.valueOf(smtpPort));
      this.smtpProperties.put("mail.smtp.starttls.enable", String.valueOf(enableStartTls));

      if (extraOptions != null) {
        this.smtpProperties.putAll(extraOptions);
      }
    }

    /**
     * Set a JavaMail SMTP property, see
     * https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html.
     */
    public @NotNull Options setSmtpProperty(@NotNull String name, String value) {
      Preconditions.checkArgument(name.startsWith("mail.smtp"), "The property is not an SMTP property");
      this.smtpProperties.put(name, value);
      return this;
    }

    /**
     * Add credentials for SMTP authentication.
     */
    public @NotNull Options setSmtpCleartextCredentials(String username, String password) {
      Preconditions.checkNotNull(username, "username");
      Preconditions.checkNotNull(password, "password");

      this.smtpProperties.put("mail.smtp.auth", "true");
      this.smtpUsername = username;
      this.smtpPassword = password;

      return this;
    }

    /**
     * Add credentials for SMTP authentication.
     */
    public @NotNull Options setSmtpSecretCredentials(String username, String secretPath) {
      Preconditions.checkNotNull(username, "username");
      Preconditions.checkNotNull(secretPath, "secretPath");

      this.smtpProperties.put("mail.smtp.auth", "true");
      this.smtpUsername = username;
      this.smtpSecretPath = secretPath;

      return this;
    }

    public @NotNull PasswordAuthentication createPasswordAuthentication(
      @NotNull SecretManagerClient adapter
    ) throws AccessException, IOException {
      //
      // Resolve authenticator on first use. To avoid holding a lock for
      // longer than necessary, we allow the lookup to occur multiple times and
      // let the first writer win.
      //
      if (this.cachedAuthentication == null)
      {
        String password;
        if (this.smtpSecretPath != null && this.smtpSecretPath.length() > 0) {
          //
          // Read password from secret manager.
          //
          password = adapter.accessSecret(this.smtpSecretPath);
        }
        else {
          //
          // Use clear-text password.
          //
          password = this.smtpPassword;
        }

        synchronized (this) {
          this.cachedAuthentication = new PasswordAuthentication(smtpUsername, password);
        }
      }

      assert this.cachedAuthentication != null;
      return this.cachedAuthentication;
    }
  }
}

