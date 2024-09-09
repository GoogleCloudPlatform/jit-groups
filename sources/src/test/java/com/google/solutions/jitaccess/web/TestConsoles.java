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

package com.google.solutions.jitaccess.web;

import com.google.solutions.jitaccess.apis.OrganizationId;
import com.google.solutions.jitaccess.apis.clients.GroupKey;
import com.google.solutions.jitaccess.catalog.auth.GroupId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestConsoles {

  //---------------------------------------------------------------------------
  // cloudConsole_groupDetails.
  //---------------------------------------------------------------------------

  @Test
  public void cloudConsole_groupDetails() {
    var consoles = new Consoles(new OrganizationId("123"));

    assertEquals(
      "https://console.cloud.google.com/iam-admin/groups/abc?organizationId=123",
      consoles.cloudConsole().groupDetails(new GroupKey("abc")));
  }

  //---------------------------------------------------------------------------
  // adminConsole_groupDetails.
  //---------------------------------------------------------------------------

  @Test
  public void adminConsole_groupDetails() {
    var consoles = new Consoles(new OrganizationId("123"));

    assertEquals(
      "https://admin.google.com/ac/groups/abc",
      consoles.adminConsole().groupDetails(new GroupKey("abc")));
  }

  //---------------------------------------------------------------------------
  // adminConsole_groupDetails.
  //---------------------------------------------------------------------------

  @Test
  public void groupsConsole_groupDetails() {
    var consoles = new Consoles(new OrganizationId("123"));

    assertEquals(
      "https://groups.google.com/a/example.com/g/group/about",
      consoles.groupsConsole().groupDetails(new GroupId("group@example.com")));
  }
}
