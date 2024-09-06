//
// Copyright 2021 Google LLC
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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestClassPrincipalSet extends TestRecord<ClassPrincipalSet> {
  @Override
  protected @NotNull ClassPrincipalSet createInstance() {
    return ClassPrincipalSet.IAP_USERS;
  }

  @Override
  protected @NotNull ClassPrincipalSet createDifferentInstance() {
    return ClassPrincipalSet.INTERNAL_USERS;
  }

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsPrefixedValue() {
    assertEquals("class:iapUsers", ClassPrincipalSet.IAP_USERS.toString());
  }

  // -------------------------------------------------------------------------
  // PrincipalId.
  // -------------------------------------------------------------------------

  @Test
  public void value() {
    assertEquals("iapUsers", ClassPrincipalSet.IAP_USERS.value());
  }

  @Test
  public void iamPrincipalId() {
    assertFalse(((Object) ClassPrincipalSet.IAP_USERS) instanceof IamPrincipalId);
  }

  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    "class",
    "class:",
    " class:  iapUsers",
    "class"
  })
  public void parse_whenInvalid(String s) {
    assertFalse(ClassPrincipalSet.parse(null).isPresent());
    assertFalse(ClassPrincipalSet.parse(s).isPresent());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    " class:iapUsers",
    "class:IAPUSERS  "
  })
  public void parse_iapUsers(String s) {
    assertEquals(ClassPrincipalSet.IAP_USERS, ClassPrincipalSet.parse(s).get());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    " class:internalUsers",
    "class:INTERNALUSERS  "
  })
  public void parse_internalUsers(String s) {
    assertEquals(ClassPrincipalSet.INTERNAL_USERS, ClassPrincipalSet.parse(s).get());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    " class:externalUsers",
    "class:EXTERNALUSERS  "
  })
  public void parse_externalUsers(String s) {
    assertEquals(ClassPrincipalSet.EXTERNAL_USERS, ClassPrincipalSet.parse(s).get());
  }
}
