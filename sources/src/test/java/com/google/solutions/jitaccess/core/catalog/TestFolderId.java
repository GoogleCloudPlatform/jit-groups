//
// Copyright 2023 Google LLC
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

package com.google.solutions.jitaccess.core.catalog;

import com.google.solutions.jitaccess.core.catalog.FolderId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

public class TestFolderId {

  @Test
  public void toStringReturnsId() {
    assertEquals("111", new FolderId("111").toString());
  }

  // -------------------------------------------------------------------------
  // Type.
  // -------------------------------------------------------------------------

  @Test
  public void type() {
    assertEquals("folder", new FolderId("111").type());
  }

  // -------------------------------------------------------------------------
  // ID.
  // -------------------------------------------------------------------------

  @Test
  public void id() {
    assertEquals("111", new FolderId("111").id());
  }

  // -------------------------------------------------------------------------
  // Path.
  // -------------------------------------------------------------------------

  @Test
  public void path() {
    assertEquals("folders/111", new FolderId("111").path());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void whenObjectAreEquivalent_ThenEqualsReturnsTrue() {
    FolderId id1 = new FolderId("111");
    FolderId id2 = new FolderId("111");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectAreSame_ThenEqualsReturnsTrue() {
    FolderId id1 = new FolderId("111");

    assertTrue(id1.equals(id1));
  }

  @Test
  public void whenObjectAreMotEquivalent_ThenEqualsReturnsFalse() {
    FolderId id1 = new FolderId("111");
    FolderId id2 = new FolderId("222");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectIsNull_ThenEqualsReturnsFalse() {
    FolderId id1 = new FolderId("111");

    assertFalse(id1.equals(null));
  }

  @Test
  public void whenObjectIsDifferentType_ThenEqualsReturnsFalse() {
    FolderId id1 = new FolderId("111");

    assertFalse(id1.equals(""));
  }
  
  // -------------------------------------------------------------------------
  // Comparable.
  // -------------------------------------------------------------------------

  @Test
  public void whenInTreeSet_ThenReturnsInExpectedOrder() {
    var folders = List.of(
      new FolderId("333"),
      new FolderId("111"),
      new FolderId("222"));

    assertIterableEquals(
      List.of(
        new FolderId("111"),
        new FolderId("222"),
        new FolderId("333")),
      new TreeSet<>(folders));
  }
}
