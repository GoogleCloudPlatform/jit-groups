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

package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.Policy;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocument;
import com.google.solutions.jitaccess.apis.IamRoleResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.time.Instant;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class TestPolicyResource {
  //---------------------------------------------------------------------------
  // lint.
  //---------------------------------------------------------------------------

  @Test
  public void lint_whenPolicyNull() {
    var resource = new PolicyResource();

    assertThrows(
      IllegalArgumentException.class,
      () -> resource.lint(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"-", ";", "invalid:;)"})
  public void lint_whenPolicyMalformed(String source) {
    var resource = new PolicyResource();

    var result = resource.lint(source);

    assertFalse(result.successful());
    assertFalse(result.issues().isEmpty());
  }

  @Test
  public void lint_whenPolicyHasNoName() {
    var resource = new PolicyResource();

    var result = resource.lint(
      "schemaVersion: 1\n" +
      "environment:\n" +
      "  name: \"\"");

    assertTrue(result.successful());
    assertTrue(result.issues().isEmpty());
  }

  @Test
  public void lint_whenPolicyContainsInvalidRoles() {
    var resource = new PolicyResource();
    resource.roleResolver = Mockito.mock(IamRoleResolver.class);
    when(resource.roleResolver.exists(any())).thenReturn(false);

    var result = resource.lint(
      "schemaVersion: 1\n" +
        "environment:\n" +
        "  name: \"environment\"\n" +
        "  systems:\n" +
        "  - name: \"system-1\"\n" +
        "    groups:\n" +
        "    - name: \"group-1\"\n" +
        "      privileges:\n" +
        "        iam:\n" +
        "        - resource: \"projects/project-1\"\n" +
        "          role: \"roles/compute.invalid-1\"\n" +
        "        - resource: \"projects/project-1\"\n" +
        "          role: \"roles/compute.invalid-2\"");

    assertFalse(result.successful());
    assertEquals(2, result.issues().size());

    assertEquals("group-1", result.issues().get(0).scope());
    assertEquals("PRIVILEGE_INVALID_ROLE", result.issues().get(0).code());

    assertEquals("group-1", result.issues().get(1).scope());
    assertEquals("PRIVILEGE_INVALID_ROLE", result.issues().get(1).code());
  }

  @Test
  public void lint() {
    var resource = new PolicyResource();

    var policy = new EnvironmentPolicy(
      "env-1",
      "Env-1",
      new Policy.Metadata("test", Instant.now()));

    var result = resource.lint(new PolicyDocument(policy).toString());

    assertTrue(result.successful());
    assertTrue(result.issues().isEmpty());
  }
}
