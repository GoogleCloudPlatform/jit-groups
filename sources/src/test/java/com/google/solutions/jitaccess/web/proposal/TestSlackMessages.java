//
// Copyright 2026 Wave Mobile Money / wavemm fork
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//

package com.google.solutions.jitaccess.web.proposal;

import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.element.ButtonElement;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TestSlackMessages {

  private static final ZoneId UTC = ZoneId.of("UTC");

  // -------------------------------------------------------------------------
  // reviewRequest — primary regression coverage. Asserts that the action
  // button URL matches the JWT URL we pass in (load-bearing: this is the
  // only way the reviewer can actually approve).
  // -------------------------------------------------------------------------

  @Test
  public void reviewRequest_carriesActionUriOnPrimaryButton() {
    var actionUri = URI.create(
      "https://pam.wavemm.net/?activation=eyJhbGciOiJSUzI1NiJ9.x.y");

    var blocks = SlackMessages.reviewRequest(
      "alice@example.com",
      "env/sys/grp",
      "Working on CASE-123",
      Instant.now().plus(1, ChronoUnit.HOURS),
      actionUri,
      UTC);

    var action = blocks.stream()
      .filter(ActionsBlock.class::isInstance)
      .map(ActionsBlock.class::cast)
      .findFirst()
      .orElseThrow(() -> new AssertionError("expected an ActionsBlock"));

    var button = action.getElements().stream()
      .filter(ButtonElement.class::isInstance)
      .map(ButtonElement.class::cast)
      .findFirst()
      .orElseThrow(() -> new AssertionError("expected a ButtonElement"));

    assertEquals(actionUri.toString(), button.getUrl());
    assertEquals("primary", button.getStyle());
  }

  @Test
  public void reviewRequest_includesRequesterAndGroupAndJustification() {
    var blocks = SlackMessages.reviewRequest(
      "alice@example.com",
      "env/sys/grp",
      "Working on CASE-123",
      Instant.parse("2030-01-01T12:00:00Z"),
      URI.create("https://pam.wavemm.net/?activation=jwt"),
      UTC);

    var serialized = blocks.toString();
    assertTrue(serialized.contains("alice@example.com"), "requester email present");
    assertTrue(serialized.contains("env/sys/grp"), "group id present");
    assertTrue(serialized.contains("CASE-123"), "justification present");
  }

  @Test
  public void reviewRequest_rendersJustificationAsPlainText() {
    // SECURITY: a malicious requester pasting Slack mrkdwn tokens into
    // the justification field MUST NOT have those tokens interpreted by
    // Slack — otherwise <!here>/<!channel>, link-spoofing
    // <https://evil/|innocent text>, or <@Uxxxx> mentions would reach
    // every reviewer's DM with attacker-controlled formatting. The
    // justification is rendered via PlainTextObject which Slack treats
    // as literal text.
    var hostile = "<!here> URGENT: <https://evil/|click here> *bold* <@U99999>";
    var blocks = SlackMessages.reviewRequest(
      "alice@example.com",
      "env/sys/grp",
      hostile,
      Instant.now().plus(1, ChronoUnit.HOURS),
      URI.create("https://pam.wavemm.net/?activation=jwt"),
      UTC);

    // Find the SectionBlock that carries the justification — it's a
    // PlainTextObject (type "plain_text"), distinct from the
    // mrkdwn-typed "Justification" header section above it.
    boolean justificationIsPlainText = blocks.stream()
      .filter(b -> b instanceof com.slack.api.model.block.SectionBlock)
      .map(b -> (com.slack.api.model.block.SectionBlock) b)
      .filter(s -> s.getText() != null
        && s.getText() instanceof com.slack.api.model.block.composition.PlainTextObject)
      .map(s -> ((com.slack.api.model.block.composition.PlainTextObject) s.getText()).getText())
      .anyMatch(t -> t.equals(hostile));
    assertTrue(justificationIsPlainText,
      "justification must reach Slack as a PlainTextObject so mrkdwn "
        + "tokens are inert");
  }

  @Test
  public void reviewRequest_truncatesOverlongJustification() {
    // SECURITY/DoS: Block Kit text fields cap at 3000 chars. A 40 KB
    // justification would otherwise fail the chat.postMessage call for
    // every reviewer, blocking the elevation entirely.
    var huge = "x".repeat(50_000);
    var blocks = SlackMessages.reviewRequest(
      "alice@example.com",
      "env/sys/grp",
      huge,
      Instant.now().plus(1, ChronoUnit.HOURS),
      URI.create("https://pam.wavemm.net/?activation=jwt"),
      UTC);

    var rendered = blocks.stream()
      .filter(b -> b instanceof com.slack.api.model.block.SectionBlock)
      .map(b -> (com.slack.api.model.block.SectionBlock) b)
      .filter(s -> s.getText() != null
        && s.getText() instanceof com.slack.api.model.block.composition.PlainTextObject)
      .map(s -> ((com.slack.api.model.block.composition.PlainTextObject) s.getText()).getText())
      .filter(t -> t.length() > 1)
      .findFirst()
      .orElseThrow();
    assertTrue(rendered.length() <= SlackMessages.justificationMaxLength(),
      "justification must be truncated to <= JUSTIFICATION_MAX_LENGTH");
    assertTrue(rendered.endsWith("[truncated]"),
      "truncation must be visible to reviewers so they don't act on "
        + "an incomplete justification");
  }

  // -------------------------------------------------------------------------
  // reviewerSiblingUpdate — must NOT carry an action button (we don't want
  // a sibling reviewer clicking after a peer already approved).
  // -------------------------------------------------------------------------

  @Test
  public void reviewerSiblingUpdate_dropsActionButton() {
    var blocks = SlackMessages.reviewerSiblingUpdate(
      "alice@example.com", "env/sys/grp", "bob@example.com");

    boolean hasActions = blocks.stream().anyMatch(ActionsBlock.class::isInstance);
    assertFalse(hasActions, "sibling-update messages must not carry action buttons");
  }

  @Test
  public void reviewerSiblingUpdate_namesApprover() {
    var blocks = SlackMessages.reviewerSiblingUpdate(
      "alice@example.com", "env/sys/grp", "bob@example.com");

    assertTrue(blocks.toString().contains("bob@example.com"),
      "sibling-update message must name the approver so reviewers know who acted");
  }

  // -------------------------------------------------------------------------
  // beneficiaryApproved — confirms to requester that the elevation landed.
  // -------------------------------------------------------------------------

  @Test
  public void beneficiaryApproved_namesGroupAndApprover() {
    var blocks = SlackMessages.beneficiaryApproved(
      "env/sys/grp", "bob@example.com");

    var serialized = blocks.toString();
    assertTrue(serialized.contains("env/sys/grp"));
    assertTrue(serialized.contains("bob@example.com"));
    assertFalse(blocks.stream().anyMatch(ActionsBlock.class::isInstance),
      "beneficiary confirmation has no further actions");
  }

  // -------------------------------------------------------------------------
  // Fallback strings (used as accessibility text + push notification body)
  // -------------------------------------------------------------------------

  @Test
  public void fallbackStrings_containKeyFields() {
    assertTrue(SlackMessages.reviewRequestFallback("alice@example.com", "env/sys/grp")
      .contains("alice@example.com"));
    assertTrue(SlackMessages.reviewerSiblingUpdateFallback("bob@example.com")
      .contains("bob@example.com"));
    assertTrue(SlackMessages.beneficiaryApprovedFallback("env/sys/grp", "bob@example.com")
      .contains("env/sys/grp"));
  }
}
