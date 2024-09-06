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

import com.google.solutions.jitaccess.TestRecord;
import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

public class TestProjectId extends TestRecord<ProjectId> {
  @Override
  protected @NotNull ProjectId createInstance() {
    return  new ProjectId("project-1");
  }

  @Override
  protected @NotNull ProjectId createDifferentInstance() {
    return  new ProjectId("project-2");
  }

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

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
  @ValueSource(strings = {
    " ",
    "123",
    "foo/bar",
    "project-1/",
    ProjectId.PREFIX,
    "projects/a/resource/b"
  })
  public void parse_whenIdInvalid(String s) {
    assertFalse(ProjectId.parse(null).isPresent());
    assertFalse(ProjectId.parse(s).isPresent());
  }

  // -------------------------------------------------------------------------
  // Service.
  // -------------------------------------------------------------------------

  @Test
  public void service() {
    assertEquals(ResourceManagerClient.SERVICE, new ProjectId("project-1").service());
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
