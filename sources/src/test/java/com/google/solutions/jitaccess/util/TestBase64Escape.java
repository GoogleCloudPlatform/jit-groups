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

package com.google.solutions.jitaccess.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestBase64Escape {

  // -------------------------------------------------------------------------
  // escape.
  // -------------------------------------------------------------------------

  @Test
  public void escape_whenStringIsNull() {
    assertNull(Base64Escape.escape(null));
  }

  @Test
  public void escape_whenStringNotIsNull() {
    assertEquals("", Base64Escape.escape(""));
    assertEquals("YQ==", Base64Escape.escape("a"));
    assertEquals("dGVzdCE=", Base64Escape.escape("test!"));
  }

  // -------------------------------------------------------------------------
  // unescape.
  // -------------------------------------------------------------------------

  @Test
  public void unescape_whenStringIsNull() {
    assertNull(Base64Escape.unescape(null));
  }

  @Test
  public void unescape_whenStringNotIsNull() {
    assertEquals("", Base64Escape.unescape(""));
    assertEquals("a", Base64Escape.unescape("YQ=="));
    assertEquals("test!", Base64Escape.unescape("dGVzdCE="));
  }
}
