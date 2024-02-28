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

package com.google.solutions.jitaccess.catalog.policy;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPolicyPermission {
  @Test
  public void view() {
    assertTrue((PolicyPermission.JOIN.toMask() & PolicyPermission.VIEW.toMask()) != 0);
  }

  //---------------------------------------------------------------------------
  // parse.
  //---------------------------------------------------------------------------

  @Test
  public void parse_join() {
    assertEquals(
      EnumSet.of(PolicyPermission.JOIN),
      PolicyPermission.parse("Join  "));
  }

  @Test
  public void parse_approveSelf() {
    assertEquals(
      EnumSet.of(PolicyPermission.APPROVE_SELF),
      PolicyPermission.parse(" approve_self  "));
  }

  @Test
  public void parse_approveOthers() {
    assertEquals(
      EnumSet.of(PolicyPermission.APPROVE_OTHERS),
      PolicyPermission.parse("APPROVE_OTHERS"));
  }

  @Test
  public void parse_list() {
    assertEquals(
      EnumSet.of(PolicyPermission.JOIN, PolicyPermission.APPROVE_SELF),
      PolicyPermission.parse("Join,approve_self,,  "));
  }

  //---------------------------------------------------------------------------
  // fromMask.
  //---------------------------------------------------------------------------

  @Test
  public void fromMask_whenSingleValue() {
    assertEquals(
      EnumSet.of(PolicyPermission.VIEW, PolicyPermission.JOIN),
      PolicyPermission.fromMask(3));
  }

  @Test
  public void fromMask_whenCombinedValue() {
    assertEquals(
      EnumSet.of(PolicyPermission.VIEW, PolicyPermission.JOIN, PolicyPermission.APPROVE_SELF),
      PolicyPermission.fromMask(11));
  }

  //---------------------------------------------------------------------------
  // toString.
  //---------------------------------------------------------------------------

  @Test
  public void toString_returnsNoBrackets() {
    assertEquals(
      "JOIN",
      PolicyPermission.toString(EnumSet.of(PolicyPermission.JOIN)));
    assertEquals(
      "JOIN, APPROVE_OTHERS, APPROVE_SELF",
      PolicyPermission.toString(
        PolicyPermission.parse("JOIN,approve_self,,approve_self,approve_others  ")));
  }
}
