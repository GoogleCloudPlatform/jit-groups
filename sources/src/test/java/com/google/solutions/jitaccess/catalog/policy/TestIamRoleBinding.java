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

import com.google.solutions.jitaccess.apis.IamRole;
import com.google.solutions.jitaccess.apis.ProjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestIamRoleBinding {
  private static final ProjectId SAMPLE_PROJECT_1 = new ProjectId("project-1");
  private static final ProjectId SAMPLE_PROJECT_2 = new ProjectId("project-2");
  private static final IamRole SAMPLE_ROLE_1 = new IamRole("roles/role-1");
  private static final IamRole SAMPLE_ROLE_2 = new IamRole("roles/role-2");
  
  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_whenDescriptionProvided() {
    var binding = new IamRoleBinding(
      new ProjectId("project-1"),
      new IamRole("roles/viewer"),
      "description",
      null);

    assertEquals("description", binding.toString());
  }

  @Test
  public void toString_whenDescriptionIsNull() {
    var binding = new IamRoleBinding(
      new ProjectId("project-1"),
      new IamRole("roles/viewer"));

    assertEquals("roles/viewer on project-1", binding.toString());
  }

  // -------------------------------------------------------------------------
  // checksum.
  // -------------------------------------------------------------------------

  @Test
  public void checksum_whenResourceVaries() {
    assertNotEquals(
      new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_1).checksum(),
      new IamRoleBinding(SAMPLE_PROJECT_2, SAMPLE_ROLE_1).checksum());
  }

  @Test
  public void checksum_whenRoleVaries() {
    assertNotEquals(
      new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_1).checksum(),
      new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_2).checksum());
  }

  @Test
  public void checksum_whenConditionVaries() {
    assertNotEquals(
      new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_1, null, "one").checksum(),
      new IamRoleBinding(SAMPLE_PROJECT_1, SAMPLE_ROLE_1, null, "two").checksum());
  }

  @Test
  public void checksum_whenDescriptionVaries() {
    assertNotEquals(
      new IamRoleBinding(SAMPLE_PROJECT_2, SAMPLE_ROLE_1, "description", "c").checksum(),
      new IamRoleBinding(SAMPLE_PROJECT_2, SAMPLE_ROLE_1, null, "c").checksum());
    assertNotEquals(
      new IamRoleBinding(SAMPLE_PROJECT_2, SAMPLE_ROLE_1, "description", "c").checksum(),
      new IamRoleBinding(SAMPLE_PROJECT_2, SAMPLE_ROLE_1, "other", "c").checksum());
  }

  @Test
  public void checksum() {
    assertEquals(
      838278625,
      new IamRoleBinding(SAMPLE_PROJECT_2, SAMPLE_ROLE_1, "description", "c").checksum());
  }
}
