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

package com.google.solutions.jitaccess.apis.clients;

import com.google.solutions.jitaccess.TestRecord;
import com.google.solutions.jitaccess.catalog.auth.EndUserId;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestGroupKey extends TestRecord<GroupKey> {
  @Override
  protected @NotNull GroupKey createInstance() {
    return new GroupKey("1");
  }

  @Override
  protected @NotNull GroupKey createDifferentInstance() {
    return new GroupKey("2");
  }
  // -------------------------------------------------------------------------
  // Constructor.
  // -------------------------------------------------------------------------

  @Test
  public void constructor_whenIdHasPrefix() {
    assertEquals("1", new GroupKey("1").id);
    assertEquals("1", new GroupKey("groups/1").id);
  }

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toString_returnsPrefixedId() {
    assertEquals("groups/1", new GroupKey("1").toString());
  }
}
