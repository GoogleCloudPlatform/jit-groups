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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestSlackMessageRegistry {

  // -------------------------------------------------------------------------
  // requestKey — must be stable, deterministic, and order-independent on
  // recipients (because the AbstractProposalHandler sorts them differently
  // on propose vs. accept depending on Set iteration order).
  // -------------------------------------------------------------------------

  @Test
  public void requestKey_isDeterministic() {
    var k1 = SlackMessageRegistry.requestKey(
      "alice@example.com",
      "env/sys/grp",
      List.of("bob@example.com", "carol@example.com"));
    var k2 = SlackMessageRegistry.requestKey(
      "alice@example.com",
      "env/sys/grp",
      List.of("bob@example.com", "carol@example.com"));
    assertEquals(k1, k2);
    assertEquals(64, k1.length(), "SHA-256 hex");
  }

  @Test
  public void requestKey_isInvariantToRecipientOrder() {
    var k1 = SlackMessageRegistry.requestKey(
      "alice@example.com",
      "env/sys/grp",
      List.of("bob@example.com", "carol@example.com"));
    var k2 = SlackMessageRegistry.requestKey(
      "alice@example.com",
      "env/sys/grp",
      List.of("carol@example.com", "bob@example.com"));
    assertEquals(k1, k2,
      "Recipient ordering must not affect the key — propose and accept "
        + "may iterate the recipients Set in different orders.");
  }

  @Test
  public void requestKey_distinguishesBeneficiary() {
    var k1 = SlackMessageRegistry.requestKey(
      "alice@example.com", "env/sys/grp", List.of("bob@example.com"));
    var k2 = SlackMessageRegistry.requestKey(
      "alex@example.com", "env/sys/grp", List.of("bob@example.com"));
    assertNotEquals(k1, k2);
  }

  @Test
  public void requestKey_distinguishesGroup() {
    var k1 = SlackMessageRegistry.requestKey(
      "alice@example.com", "env/sys/grp-1", List.of("bob@example.com"));
    var k2 = SlackMessageRegistry.requestKey(
      "alice@example.com", "env/sys/grp-2", List.of("bob@example.com"));
    assertNotEquals(k1, k2);
  }

  @Test
  public void requestKey_distinguishesRecipients() {
    var k1 = SlackMessageRegistry.requestKey(
      "alice@example.com", "env/sys/grp", List.of("bob@example.com"));
    var k2 = SlackMessageRegistry.requestKey(
      "alice@example.com", "env/sys/grp", List.of("carol@example.com"));
    assertNotEquals(k1, k2);
  }

  @Test
  public void requestKey_emptyRecipientsIsValid() {
    // Empty recipients shouldn't crash even though no real flow produces it.
    var k = SlackMessageRegistry.requestKey(
      "alice@example.com", "env/sys/grp", List.of());
    assertEquals(64, k.length());
  }
}
