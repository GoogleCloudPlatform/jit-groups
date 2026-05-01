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

import com.google.cloud.firestore.Firestore;
import com.google.solutions.jitaccess.apis.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

public class TestSlackMessageRegistry {

  private static final String SALT_A = "test-salt-A-must-be-at-least-32-bytes-long-xxx";
  private static final String SALT_B = "test-salt-B-different-from-A-also-32-bytes-yyy";

  private static SlackMessageRegistry newRegistry(String salt) {
    return new SlackMessageRegistry(
      Mockito.mock(Firestore.class),
      Mockito.mock(Executor.class),
      Mockito.mock(Logger.class),
      salt);
  }

  // -------------------------------------------------------------------------
  // requestKey — must be stable, deterministic, and order-independent on
  // recipients (because the AbstractProposalHandler sorts them differently
  // on propose vs. accept depending on Set iteration order).
  // -------------------------------------------------------------------------

  @Test
  public void requestKey_isDeterministic() {
    var registry = newRegistry(SALT_A);
    var k1 = registry.requestKey(
      "alice@example.com",
      "env/sys/grp",
      List.of("bob@example.com", "carol@example.com"));
    var k2 = registry.requestKey(
      "alice@example.com",
      "env/sys/grp",
      List.of("bob@example.com", "carol@example.com"));
    assertEquals(k1, k2);
    assertEquals(64, k1.length(), "HMAC-SHA-256 hex");
  }

  @Test
  public void requestKey_isInvariantToRecipientOrder() {
    var registry = newRegistry(SALT_A);
    var k1 = registry.requestKey(
      "alice@example.com",
      "env/sys/grp",
      List.of("bob@example.com", "carol@example.com"));
    var k2 = registry.requestKey(
      "alice@example.com",
      "env/sys/grp",
      List.of("carol@example.com", "bob@example.com"));
    assertEquals(k1, k2,
      "Recipient ordering must not affect the key — propose and accept "
        + "may iterate the recipients Set in different orders.");
  }

  @Test
  public void requestKey_distinguishesBeneficiary() {
    var registry = newRegistry(SALT_A);
    var k1 = registry.requestKey(
      "alice@example.com", "env/sys/grp", List.of("bob@example.com"));
    var k2 = registry.requestKey(
      "alex@example.com", "env/sys/grp", List.of("bob@example.com"));
    assertNotEquals(k1, k2);
  }

  @Test
  public void requestKey_distinguishesGroup() {
    var registry = newRegistry(SALT_A);
    var k1 = registry.requestKey(
      "alice@example.com", "env/sys/grp-1", List.of("bob@example.com"));
    var k2 = registry.requestKey(
      "alice@example.com", "env/sys/grp-2", List.of("bob@example.com"));
    assertNotEquals(k1, k2);
  }

  @Test
  public void requestKey_distinguishesRecipients() {
    var registry = newRegistry(SALT_A);
    var k1 = registry.requestKey(
      "alice@example.com", "env/sys/grp", List.of("bob@example.com"));
    var k2 = registry.requestKey(
      "alice@example.com", "env/sys/grp", List.of("carol@example.com"));
    assertNotEquals(k1, k2);
  }

  @Test
  public void requestKey_emptyRecipientsIsValid() {
    var registry = newRegistry(SALT_A);
    // Empty recipients shouldn't crash even though no real flow produces it.
    var k = registry.requestKey(
      "alice@example.com", "env/sys/grp", List.of());
    assertEquals(64, k.length());
  }

  /**
   * Wavemm fork P2-11 regression: the salt MUST influence the output —
   * otherwise an attacker who reads {(beneficiary, group, recipients)}
   * from logs could enumerate registry contents by computing SHA-256
   * themselves. With distinct salts the same input must produce
   * distinct keys.
   */
  @Test
  public void requestKey_distinguishesSalt() {
    var registryA = newRegistry(SALT_A);
    var registryB = newRegistry(SALT_B);

    var inputs = List.of("bob@example.com");
    var kA = registryA.requestKey("alice@example.com", "env/sys/grp", inputs);
    var kB = registryB.requestKey("alice@example.com", "env/sys/grp", inputs);

    assertNotEquals(kA, kB,
      "HMAC under different salts must produce different keys — "
        + "otherwise the salt has no effect and an attacker who reads "
        + "input tuples can recover the key with public SHA-256.");
  }

  @Test
  public void constructor_rejectsBlankSalt() {
    // A blank salt would silently produce an HMAC under an empty key,
    // defeating the secret-derived security property entirely.
    assertThrows(IllegalArgumentException.class, () -> newRegistry(""));
    assertThrows(IllegalArgumentException.class, () -> newRegistry("   "));
  }
}
