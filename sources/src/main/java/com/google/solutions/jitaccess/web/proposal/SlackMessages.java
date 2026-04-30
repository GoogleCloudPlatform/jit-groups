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
import com.slack.api.model.block.ContextBlock;
import com.slack.api.model.block.DividerBlock;
import com.slack.api.model.block.HeaderBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.ButtonElement;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Block Kit message templates for the Slack notification flow.
 *
 * <p>Three message shapes:
 * <ol>
 *   <li>{@link #reviewRequest} — DM sent to each qualified reviewer when an
 *       MPA elevation is requested. Carries an "Approve in JIT" button that
 *       links to the JIT approval URL (the JWT-bearing action URI).
 *   <li>{@link #reviewerSiblingUpdate} — replaces a sibling reviewer's
 *       request DM after another reviewer approves. Strips the action button
 *       and adds an "approved by X" context line.
 *   <li>{@link #beneficiaryApproved} — DM to the requester confirming the
 *       activation, naming the approver.
 * </ol>
 */
final class SlackMessages {
  private static final DateTimeFormatter HUMAN_TIME = DateTimeFormatter
    .ofPattern("EEE d MMM, HH:mm z");

  private SlackMessages() {}

  /**
   * Builds the request DM sent to a reviewer.
   *
   * @param requesterEmail beneficiary's email (formatted in body)
   * @param groupId JIT group being joined (formatted in body)
   * @param justification user-provided text from the JIT submit form
   * @param expiry token expiry (relative timing rendered in body)
   * @param actionUri JWT-bearing URL the reviewer clicks to approve
   * @param tz time zone for rendering expiry
   */
  static List<LayoutBlock> reviewRequest(
    @NotNull String requesterEmail,
    @NotNull String groupId,
    @NotNull String justification,
    @NotNull Instant expiry,
    @NotNull URI actionUri,
    @NotNull ZoneId tz
  ) {
    var blocks = new ArrayList<LayoutBlock>();

    blocks.add(HeaderBlock.builder()
      .text(plain(":lock: PAM elevation request — your approval is needed"))
      .build());

    blocks.add(SectionBlock.builder()
      .fields(List.of(
        markdownField("*Requester*", "<mailto:" + requesterEmail + "|" + requesterEmail + ">"),
        markdownField("*Group*", "`" + groupId + "`"),
        markdownField("*Expires*", relative(expiry, tz)),
        markdownField("*Type*", "Multi-party approval")
      ))
      .build());

    blocks.add(SectionBlock.builder()
      .text(markdown("*Justification*\n>" + escapeBlockquote(justification)))
      .build());

    blocks.add(DividerBlock.builder().build());

    blocks.add(ActionsBlock.builder()
      .elements(List.of(
        ButtonElement.builder()
          .text(plain("Approve in JIT"))
          .url(actionUri.toString())
          .style("primary")
          .build()))
      .build());

    blocks.add(ContextBlock.builder()
      .elements(List.of(markdown(
        ":eyes: Approval is final — review the request carefully. "
          + "Approving opens the JIT page where you confirm.")))
      .build());

    return blocks;
  }

  /**
   * Replaces the reviewer's request DM after a peer has approved.
   * The action button is removed; a small context line names the approver.
   */
  static List<LayoutBlock> reviewerSiblingUpdate(
    @NotNull String requesterEmail,
    @NotNull String groupId,
    @NotNull String approverEmail
  ) {
    return List.of(
      HeaderBlock.builder()
        .text(plain(":white_check_mark: Already approved — no action needed"))
        .build(),
      SectionBlock.builder()
        .fields(List.of(
          markdownField("*Requester*", "<mailto:" + requesterEmail + "|" + requesterEmail + ">"),
          markdownField("*Group*", "`" + groupId + "`"),
          markdownField("*Approved by*", "<mailto:" + approverEmail + "|" + approverEmail + ">")
        ))
        .build(),
      ContextBlock.builder()
        .elements(List.of(markdown(
          ":information_source: Another reviewer approved this request. "
            + "You can ignore this message.")))
        .build());
  }

  /**
   * DM to the beneficiary confirming the activation completed.
   */
  static List<LayoutBlock> beneficiaryApproved(
    @NotNull String groupId,
    @NotNull String approverEmail
  ) {
    return List.of(
      HeaderBlock.builder()
        .text(plain(":white_check_mark: PAM elevation approved"))
        .build(),
      SectionBlock.builder()
        .fields(List.of(
          markdownField("*Group*", "`" + groupId + "`"),
          markdownField("*Approved by*", "<mailto:" + approverEmail + "|" + approverEmail + ">")
        ))
        .build(),
      ContextBlock.builder()
        .elements(List.of(markdown(
          "Your access is active until token expiry. Refresh `pam.wavemm.net` "
            + "or `wavecli pam` to use it.")))
        .build());
  }

  // ----- fallback text for notifications + a11y --------------------------

  static String reviewRequestFallback(@NotNull String requesterEmail, @NotNull String groupId) {
    return String.format(
      "PAM approval requested by %s for %s — open the JIT app to approve.",
      requesterEmail, groupId);
  }

  static String reviewerSiblingUpdateFallback(@NotNull String approverEmail) {
    return "Already approved by " + approverEmail + " — no action needed.";
  }

  static String beneficiaryApprovedFallback(@NotNull String groupId, @NotNull String approverEmail) {
    return String.format("Your PAM elevation for %s was approved by %s.", groupId, approverEmail);
  }

  // ----- helpers ---------------------------------------------------------

  private static @NotNull PlainTextObject plain(@NotNull String text) {
    return PlainTextObject.builder().text(text).emoji(true).build();
  }

  private static @NotNull MarkdownTextObject markdown(@NotNull String text) {
    return MarkdownTextObject.builder().text(text).build();
  }

  private static @NotNull MarkdownTextObject markdownField(@NotNull String label, @NotNull String value) {
    return markdown(label + "\n" + value);
  }

  private static @NotNull String relative(@NotNull Instant expiry, @NotNull ZoneId tz) {
    var remaining = Duration.between(Instant.now(), expiry);
    var minutes = Math.max(0, remaining.toMinutes());
    var humanTime = HUMAN_TIME.format(expiry.atZone(tz));
    if (minutes < 60) {
      return humanTime + " (~" + minutes + "m from now)";
    }
    return humanTime + " (~" + (minutes / 60) + "h " + (minutes % 60) + "m from now)";
  }

  private static @NotNull String escapeBlockquote(@NotNull String text) {
    // Slack blockquotes use leading > on each newline. Escape pre-existing
    // ones so a malicious justification can't inject formatting.
    return text.replace("\n", "\n>").replace("`", "'");
  }

  /** Used by tests to assert specific field values without parsing JSON. */
  static Map<String, String> debugFields(@NotNull String requesterEmail, @NotNull String groupId) {
    return Map.of("requester", requesterEmail, "group", groupId);
  }
}
