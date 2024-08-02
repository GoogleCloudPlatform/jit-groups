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

package com.google.solutions.jitaccess.web.proposal;

import com.google.api.client.json.GenericJson;
import com.google.common.html.HtmlEscapers;
import com.google.solutions.jitaccess.apis.clients.SmtpClient;
import com.google.solutions.jitaccess.catalog.Proposal;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.auth.EmailAddress;
import com.google.solutions.jitaccess.catalog.auth.EmailMapping;
import com.google.solutions.jitaccess.catalog.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.policy.Property;
import com.google.solutions.jitaccess.cel.EvaluationException;
import com.google.solutions.jitaccess.cel.StringTemplate;
import com.google.solutions.jitaccess.util.DurationFormatter;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements proposals by sending emails.
 */
public class MailProposalHandler extends AbstractProposalHandler {
  private static final @NotNull SecureRandom RANDOM = new SecureRandom();

  /**
   * Name of resource that contains the request template.
   */
  static final String PROPOSAL_TEMPLATE = "mail-templates/proposal.html";
  static final String PROPOSAL_APPROVED_TEMPLATE = "mail-templates/proposal-approved.html";
  private final @NotNull Options options;
  private final @NotNull EmailMapping emailMapping;
  private final @NotNull SmtpClient smtpClient;

  public MailProposalHandler(
    @NotNull TokenSigner tokenSigner,
    @NotNull EmailMapping emailMapping,
    @NotNull SmtpClient smtpClient,
    @NotNull Options options
  ) {
    super(
      tokenSigner,
      RANDOM,
      new AbstractProposalHandler.Options(options.tokenExpiry));
    this.emailMapping = emailMapping;
    this.smtpClient = smtpClient;
    this.options = options;
  }

  void sendMail(
    @NotNull Collection<EmailAddress> to,
    @NotNull Collection<EmailAddress> cc,
    @NotNull String subject,
    @NotNull String message,
    boolean isReply
  ) throws IOException {
    try {
      this.smtpClient.sendMail(
        to,
        cc,
        subject,
        message,
        isReply
          ? EnumSet.of(SmtpClient.Flags.REPLY)
          : EnumSet.of(SmtpClient.Flags.NONE));
    }
    catch (SmtpClient.MailException e) {
      throw new IOException("Sending email failed", e);
    }
  }

  //---------------------------------------------------------------------------
  // Overrides.
  //---------------------------------------------------------------------------

  @Override
  void onOperationProposed(
    @NotNull JitGroupContext.JoinOperation operation,
    @NotNull Proposal proposal,
    @NotNull ProposalHandler.ProposalToken token,
    @NotNull URI actionUri
  ) throws IOException {

    var recipients = proposal.recipients()
      .stream()
      .map(this.emailMapping::emailFromPrincipalId)
      .collect(Collectors.toSet());

    //
    // Load and initialize the email template.
    //
    var template = MailTemplate.fromResource(PROPOSAL_TEMPLATE);
    template.addContext("input", operation.input(), true);
    template.addContext("user", operation.user());
    template.addContext("group", operation.group());
    template.addContext("proposal")
      .set("action_uri", actionUri.toString())
      .set("token", TokenObfuscator.encode(token.value()))
      .set("expiry", OffsetDateTime
        .ofInstant(proposal.expiry(), this.options.timeZone)
        .truncatedTo(ChronoUnit.SECONDS)
        .format(DateTimeFormatter.RFC_1123_DATE_TIME));

    try {
      //
      // Send mail to users that can handle the proposal and cc
      // the user that wants to join.
      //
      sendMail(
        recipients,
        List.of(this.emailMapping.emailFromPrincipalId(proposal.user())),
        String.format(
          "%s wants to join %s",
          operation.user().email,
          operation.group().name()),
        template.evaluate(),
        false);
    }
    catch (EvaluationException e) {
      throw new IllegalArgumentException("The mail template is invalid", e);
    }
  }

  @Override
  void onProposalApproved(
    @NotNull JitGroupContext.ApprovalOperation operation,
    @NotNull Proposal proposal
  ) throws IOException {
    //
    // Load and initialize the email template.
    //
    var template = MailTemplate.fromResource(PROPOSAL_APPROVED_TEMPLATE);
    template.addContext("input", operation.input(), true);
    template.addContext("joining_user", operation.joiningUser());
    template.addContext("user", operation.user());
    template.addContext("group", operation.group());

    try {
      //
      // Send mail to joining user and cc others.
      //
      // NB. Use same subject as before so that Gmail recognizes the
      // emails as a thread.
      //
      sendMail(
        List.of(this.emailMapping.emailFromPrincipalId(proposal.user())),
        proposal.recipients()
          .stream()
          .map(this.emailMapping::emailFromPrincipalId)
          .collect(Collectors.toSet()),
        String.format(
          "%s wants to join %s",
          operation.user().email,
          operation.group().name()),
        template.evaluate(),
        true);
    }
    catch (EvaluationException e) {
      throw new IllegalArgumentException("The mail template is invalid", e);
    }
  }

  /**
   * Template for an email message.
   */
  static class MailTemplate extends StringTemplate {
    MailTemplate(@NotNull String template) {
      super(template);
    }

    static @NotNull MailTemplate fromResource(@NotNull String resourceName) throws IOException {
      try (var stream = MailTemplate.class
        .getClassLoader()
        .getResourceAsStream(resourceName)) {

        if (stream == null) {
          throw new FileNotFoundException(resourceName);
        }

        var content = stream.readAllBytes();
        if (content.length > 3 &&
          content[0] == (byte)0xEF &&
          content[1] == (byte)0xBB &&
          content[2] == (byte)0xBF) {

          //
          // Strip UTF-8 BOM.
          //
          return new MailTemplate(new String(content, 3, content.length - 3));
        }
        else {
          return new MailTemplate(new String(content));
        }
      }
    }

    static @NotNull String formatProperty(Property p) {
      if (p.type() == String.class) {
        return HtmlEscapers.htmlEscaper().escape(p.get());
      }
      else if (p.type() == Duration.class) {
        return DurationFormatter.format(Duration.parse(p.get()));
      }
      else {
        return p.get();
      }
    }

    /**
     * Add context based on a list of properties.
     */
    public Context addContext(
      @NotNull String name,
      @NotNull Collection<Property> properties,
      boolean formatted
    ) {
      var context = addContext(name);

      for (var property : properties) {
        context.set(
          property.name(),
          new GenericJson()
            .set("display_name", property.displayName())
            .set("value", formatted ? formatProperty(property) : property.get()));
      }

      return context;
    }

    public Context addContext(
      @NotNull String name,
      @NotNull UserId user
    ) {
      return addContext(name)
        .set("email", user.email);
    }

    public Context addContext(
      @NotNull String name,
      @NotNull JitGroupId group
    ) {
      return addContext(name)
        .set("environment", group.environment())
        .set("system", group.system())
        .set("name", group.name())
        .set("id", group.value());
    }
  }


  public record Options(
    @NotNull ZoneId timeZone,
    @NotNull Duration tokenExpiry
  ) {
    public static final ZoneId DEFAULT_TIMEZONE = ZoneOffset.UTC;
  }
}
