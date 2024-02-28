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

import java.nio.charset.Charset;
import java.util.Base64;

/**
 * Utility class for Base64 escaping.
 */
public class Base64Escape {
  private Base64Escape() {}

  /**
   * Escape a string.
   * @return base64 string.
   */
  public static String escape(String s) {
    if (s == null) {
      return null;
    }

    return Base64.getEncoder().encodeToString(s.getBytes(Charset.defaultCharset()));
  }

  /**
   * Unescape a string.
   * @return unescaped string.
   */
  public static String unescape(String s) {
    if (s == null) {
      return null;
    }

    return new String(Base64.getDecoder().decode(s), Charset.defaultCharset());
  }
}
