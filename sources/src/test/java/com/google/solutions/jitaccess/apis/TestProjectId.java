//
// Copyright 2022 Google LLC
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

package com.google.solutions.jitaccess.apis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

public class TestProjectId {
  private final String SAMPLE_PROJECT_FULLRESOURCENAME =
    "//cloudresourcemanager.googleapis.com/projects/project-1";

  @Test
  public void toString_returnsId() {
    assertEquals("project-1", new ProjectId("project-1").toString());
  }

  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @Test
  public void parse_whenIdPrefixed() {
    var id = ProjectId.parse(ProjectId.PREFIX + "project-1");

    assertTrue(id.isPresent());
    assertEquals("project-1", id.get().toString());
  }

  @Test
  public void parse_whenIdNotPrefixed() {
    var id = ProjectId.parse(" project-1 ");

    assertTrue(id.isPresent());
    assertEquals("project-1", id.get().toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "123", "foo/bar", "project-1/", ProjectId.PREFIX, "projects/a/resource/b"})
  public void parse_whenIdInvalid(String s) {
    assertFalse(ProjectId.parse(null).isPresent());
    assertFalse(ProjectId.parse(s).isPresent());
  }

  // -------------------------------------------------------------------------
  // Type.
  // -------------------------------------------------------------------------

  @Test
  public void type() {
    assertEquals("project", new ProjectId("project-1").type());
  }

  // -------------------------------------------------------------------------
  // ID.
  // -------------------------------------------------------------------------

  @Test
  public void id() {
    assertEquals("project-1", new ProjectId("project-1").id());
  }

  // -------------------------------------------------------------------------
  // Path.
  // -------------------------------------------------------------------------

  @Test
  public void path() {
    assertEquals("projects/project-1", new ProjectId("project-1").path());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void equals_whenObjectAreEquivalent() {
    ProjectId id1 = new ProjectId("project-1");
    ProjectId id2 = new ProjectId("project-1");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void equals_whenObjectAreSame() {
    ProjectId id1 = new ProjectId("project-1");

    assertTrue(id1.equals(id1));
  }

  @Test
  public void equals_whenObjectAreMotEquivalent() {
    ProjectId id1 = new ProjectId("project-1");
    ProjectId id2 = new ProjectId("project-2");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void equals_whenObjectIsNull() {
    ProjectId id1 = new ProjectId("project-1");

    assertFalse(id1.equals(null));
  }

  @Test
  public void equals_whenObjectIsDifferentType() {
    ProjectId id1 = new ProjectId("project-1");

    assertFalse(id1.equals(""));
  }

  // -------------------------------------------------------------------------
  // Comparable.
  // -------------------------------------------------------------------------

  @Test
  public void compareTo() {
    var projects = List.of(
      new ProjectId("project-3"),
      new ProjectId("project-1"),
      new ProjectId("project-2"));

    assertIterableEquals(
      List.of(
        new ProjectId("project-1"),
        new ProjectId("project-2"),
        new ProjectId("project-3")),
      new TreeSet<>(projects));
  }
}
