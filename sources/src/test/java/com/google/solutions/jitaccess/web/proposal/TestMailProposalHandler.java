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

import com.google.solutions.jitaccess.apis.clients.SmtpClient;
import com.google.solutions.jitaccess.catalog.JitGroupContext;
import com.google.solutions.jitaccess.catalog.Proposal;
import com.google.solutions.jitaccess.auth.EmailAddress;
import com.google.solutions.jitaccess.auth.EmailMapping;
import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.auth.EndUserId;
import com.google.solutions.jitaccess.catalog.policy.Property;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.FileNotFoundException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestMailProposalHandler {
  private final EndUserId SAMPLE_USER_1 = new EndUserId("user-1@example.com");
  private final EndUserId SAMPLE_USER_2 = new EndUserId("user-2@example.com");
  private final JitGroupId SAMPLE_JITGROUP = new JitGroupId("env-1", "sys-1", "grp-1");

  //-------------------------------------------------------------------------
  // onJoinOperationProposed.
  //-------------------------------------------------------------------------

  @Test
  public void onJoinOperationProposed() throws Exception {
    var smtpClient = Mockito.mock(SmtpClient.class);

    var handler = new MailProposalHandler(
      Mockito.mock(TokenSigner.class),
      new EmailMapping("user.email"),
      smtpClient,
      new MailProposalHandler.Options(
        MailProposalHandler.Options.DEFAULT_TIMEZONE,
        Duration.ofMinutes(1)));

    var op = Mockito.mock(JitGroupContext.JoinOperation.class);
    when(op.joiningUser()).thenReturn(SAMPLE_USER_1);
    when(op.user()).thenReturn(SAMPLE_USER_1);
    when(op.group()).thenReturn(SAMPLE_JITGROUP);
    when(op.input()).thenReturn(List.of());

    var proposal = Mockito.mock(Proposal.class);
    when(proposal.user()).thenReturn(SAMPLE_USER_1);
    when(proposal.recipients()).thenReturn(Set.of(SAMPLE_USER_2));
    when(proposal.expiry()).thenReturn(Instant.now().plusSeconds(10));

    handler.onOperationProposed(
      op,
      proposal,
      new ProposalHandler.ProposalToken(
        "eytoken",
        Set.of(SAMPLE_USER_2),
        proposal.expiry()),
      new URI("/"));

    verify(smtpClient, times(1)).sendMail(
      eq(Set.of(new EmailAddress(SAMPLE_USER_2.email))),
      eq(List.of(new EmailAddress(SAMPLE_USER_1.email))),
      eq(SAMPLE_USER_1.email + " requests to join " + SAMPLE_JITGROUP.name()),
      anyString(),
      eq(EnumSet.of(SmtpClient.Flags.NONE)));
  }

  //-------------------------------------------------------------------------
  // onProposalApproved.
  //-------------------------------------------------------------------------

  @Test
  public void onProposalApproved() throws Exception {
    var smtpClient = Mockito.mock(SmtpClient.class);

    var handler = new MailProposalHandler(
      Mockito.mock(TokenSigner.class),
      new EmailMapping("user.email"),
      smtpClient,
      new MailProposalHandler.Options(
        MailProposalHandler.Options.DEFAULT_TIMEZONE,
        Duration.ofMinutes(1)));

    var op = Mockito.mock(JitGroupContext.ApprovalOperation.class);
    when(op.joiningUser()).thenReturn(SAMPLE_USER_1);
    when(op.user()).thenReturn(SAMPLE_USER_2);
    when(op.group()).thenReturn(SAMPLE_JITGROUP);
    when(op.input()).thenReturn(List.of());

    var proposal = Mockito.mock(Proposal.class);
    when(proposal.user()).thenReturn(SAMPLE_USER_1);
    when(proposal.recipients()).thenReturn(Set.of(SAMPLE_USER_2));
    when(proposal.expiry()).thenReturn(Instant.now().plusSeconds(10));

    handler.onProposalApproved(
      op,
      proposal);

    verify(smtpClient, times(1)).sendMail(
      eq(List.of(new EmailAddress(SAMPLE_USER_1.email))),
      eq(Set.of(new EmailAddress(SAMPLE_USER_2.email))),
      eq(SAMPLE_USER_1.email + " requests to join " + SAMPLE_JITGROUP.name()),
      anyString(),
      eq(EnumSet.of(SmtpClient.Flags.REPLY)));
  }

  @Nested
  public static class MailTemplate {

    //-------------------------------------------------------------------------
    // fromResource.
    //-------------------------------------------------------------------------

    @Test
    public void fromResource_whenResourceNotFound() {
      assertThrows(
        FileNotFoundException.class,
        () -> MailProposalHandler.MailTemplate.fromResource("unknown"));
    }

    @Test
    public void fromResource() throws Exception {
      var template = MailProposalHandler.MailTemplate.fromResource(
        MailProposalHandler.PROPOSAL_TEMPLATE);
      assertNotNull(template.toString());
    }


    //-------------------------------------------------------------------------
    // formatProperty.
    //-------------------------------------------------------------------------

    @Test
    public void formatProperty_whenPropertyIsString() {
      var property = Mockito.mock(Property.class);
      when(property.type()).thenAnswer(x -> String.class);
      when(property.get()).thenReturn("<html/>");

      assertEquals(
        "&lt;html/&gt;",
        MailProposalHandler.MailTemplate.formatProperty(property));
    }

    @Test
    public void formatProperty_whenPropertyIsDuration() {
      var property = Mockito.mock(Property.class);
      when(property.type()).thenAnswer(x -> Duration.class);
      when(property.get()).thenReturn("P1DT1M");

      assertEquals(
        "1 day, 1 minute",
        MailProposalHandler.MailTemplate.formatProperty(property));
    }

    @Test
    public void formatProperty_whenPropertyIsLong() {
      var property = Mockito.mock(Property.class);
      when(property.type()).thenAnswer(x -> Long.class);
      when(property.get()).thenReturn("123456789000");

      assertEquals(
        "123456789000",
        MailProposalHandler.MailTemplate.formatProperty(property));
    }

    @Test
    public void formatProperty_whenPropertyIsBoolean() {
      var property = Mockito.mock(Property.class);
      when(property.type()).thenAnswer(x -> Boolean.class);
      when(property.get()).thenReturn("TRUE");

      assertEquals(
        "TRUE",
        MailProposalHandler.MailTemplate.formatProperty(property));
    }

    //-------------------------------------------------------------------------
    // evaluate.
    //-------------------------------------------------------------------------

    @Test
    public void evaluate_whenContextIsFormatted() throws Exception {
      var property = Mockito.mock(Property.class);
      when(property.type()).thenAnswer(x -> String.class);
      when(property.displayName()).thenReturn("Text");
      when(property.name()).thenReturn("text");
      when(property.get()).thenReturn("<b>Text</b>");

      var template = new MailProposalHandler.MailTemplate(
        "{{ input.text.display_name }} = {{ input.text.value }}");
      template.addContext(
        "input",
        List.of(property),
        true);

      assertEquals(
        "Text = &lt;b&gt;Text&lt;/b&gt;",
        template.evaluate());

    }
  }
}
