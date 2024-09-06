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

package com.google.solutions.jitaccess.catalog.auth;

import com.google.solutions.jitaccess.TestRecord;
import com.google.solutions.jitaccess.apis.Domain;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestCloudIdentityDirectoryPrincipalSet extends TestRecord<CloudIdentityDirectoryPrincipalSet> {
  @Override
  protected @NotNull CloudIdentityDirectoryPrincipalSet createInstance() {
    return new CloudIdentityDirectoryPrincipalSet("example.com");
  }

  @Override
  protected @NotNull CloudIdentityDirectoryPrincipalSet createDifferentInstance() {
    return new CloudIdentityDirectoryPrincipalSet("example.org");
  }

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsPrefixedValue() {
    assertEquals(
      "domain:example.com",
      new CloudIdentityDirectoryPrincipalSet("example.com").toString());
  }

  // -------------------------------------------------------------------------
  // domain.
  // -------------------------------------------------------------------------

  @Test
  public void domain() {
    assertEquals(
      new Domain("example.com", Domain.Type.PRIMARY),
      new CloudIdentityDirectoryPrincipalSet("example.com").domain());
    assertEquals(
      Domain.Type.PRIMARY,
      new CloudIdentityDirectoryPrincipalSet("example.com").domain().type());
  }

  // -------------------------------------------------------------------------
  // PrincipalId.
  // -------------------------------------------------------------------------

  @Test
  public void value() {
    assertEquals(
      "example.com",
      new CloudIdentityDirectoryPrincipalSet("example.com").value());
  }

  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    "domain",
    "domain:  ",
    "domain:domain:example.com  ",
    "domain"
  })
  public void parse_whenInvalid(String s) {
    assertFalse(CloudIdentityDirectoryPrincipalSet.parse(null).isPresent());
    assertFalse(CloudIdentityDirectoryPrincipalSet.parse(s).isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    " domain:example.com ",
    " domain:  example.com",
    "domain:EXAMPLE.com  "
  })
  public void parse(String s) {
    assertEquals(
      new CloudIdentityDirectoryPrincipalSet("example.com"),
      CloudIdentityDirectoryPrincipalSet.parse(s).get());
  }
}
