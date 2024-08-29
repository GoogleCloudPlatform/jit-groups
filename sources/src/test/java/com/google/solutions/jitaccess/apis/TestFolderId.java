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

import com.google.solutions.jitaccess.apis.clients.ResourceManagerClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

public class TestFolderId {
  @Test
  public void toString_returnsId() {
    assertEquals("100000000000000001", new FolderId("100000000000000001").toString());
  }

  // -------------------------------------------------------------------------
  // parse.
  // -------------------------------------------------------------------------

  @Test
  public void parse_whenIdPrefixed() {
    var id = FolderId.parse(FolderId.PREFIX + "100000000000000001");

    assertTrue(id.isPresent());
    assertEquals("100000000000000001", id.get().toString());
  }

  @Test
  public void parse_whenIdNotPrefixed() {
    var id = FolderId.parse(" 100000000000000001 ");

    assertTrue(id.isPresent());
    assertEquals("100000000000000001", id.get().toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    " ",
    "name",
    "foo/bar",
    "100000000000000001/",
    FolderId.PREFIX,
    "folders//1",
    "folders/1/resource/b"
  })
  public void parse_whenIdInvalid(String s) {
    assertFalse(FolderId.parse(null).isPresent());
    assertFalse(FolderId.parse(s).isPresent());
  }

  // -------------------------------------------------------------------------
  // Service.
  // -------------------------------------------------------------------------

  @Test
  public void service() {
    assertEquals(ResourceManagerClient.SERVICE, new FolderId("100000000000000001").service());
  }


  // -------------------------------------------------------------------------
  // Type.
  // -------------------------------------------------------------------------

  @Test
  public void type() {
    assertEquals("folder", new FolderId("100000000000000001").type());
  }

  // -------------------------------------------------------------------------
  // ID.
  // -------------------------------------------------------------------------

  @Test
  public void id() {
    assertEquals("100000000000000001", new FolderId("100000000000000001").id());
  }

  // -------------------------------------------------------------------------
  // Path.
  // -------------------------------------------------------------------------

  @Test
  public void path() {
    assertEquals("folders/100000000000000001", new FolderId("100000000000000001").path());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void equals_whenObjectAreEquivalent() {
    FolderId id1 = new FolderId("100000000000000001");
    FolderId id2 = new FolderId("100000000000000001");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void equals_whenObjectAreSame() {
    FolderId id1 = new FolderId("100000000000000001");

    assertTrue(id1.equals(id1));
  }

  @Test
  public void equals_whenObjectAreMotEquivalent() {
    FolderId id1 = new FolderId("100000000000000001");
    FolderId id2 = new FolderId("100000000000000002");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void equals_whenObjectIsNull() {
    FolderId id1 = new FolderId("100000000000000001");

    assertFalse(id1.equals(null));
  }

  @Test
  public void equals_whenObjectIsDifferentType() {
    FolderId id1 = new FolderId("100000000000000001");

    assertFalse(id1.equals(""));
  }

  // -------------------------------------------------------------------------
  // Comparable.
  // -------------------------------------------------------------------------

  @Test
  public void compareTo() {
    var projects = List.of(
      new FolderId("10003"),
      new FolderId("10001"),
      new FolderId("10002"));

    assertIterableEquals(
      List.of(
        new FolderId("10001"),
        new FolderId("10002"),
        new FolderId("10003")),
      new TreeSet<>(projects));
  }
}
