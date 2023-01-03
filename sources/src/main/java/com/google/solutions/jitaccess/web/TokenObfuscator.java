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

package com.google.solutions.jitaccess.web;

import com.google.common.base.Preconditions;

/**
 * Obfuscator for JWTs.
 *
 * Token obfuscation is not a security feature. Instead, it's used to
 * prevent false flagging by web filters or phishing filters: Some filters
 * (naively) assume that anything that looks like a JWT must be confidential,
 * and that "leaking such a credential" in a URL must be a phishing attempt.
 *
 * Some JWTs aren't credentials, and aren't even confidential. By obfuscating
 * such non-confidential JWTs, we can reduce the risk that an overzealous
 * filter falsely flags and blocks requests that contain such JWTs.
 */
public class TokenObfuscator {
  private TokenObfuscator() {}

  public static String encode(String jwt) {
    Preconditions.checkNotNull(jwt);
    Preconditions.checkArgument(jwt.startsWith("ey"));

    //
    // JWTs start with "ey" and use dots to separate the segments.
    // By removing the prefix and changing the separator, we
    // disturb Base64-decodeability and make it look less like a JWT.
    //
    return jwt.substring(2)
      .replace(".ey", "~~")
      .replace('.', '~');
  }

  public static String decode(String encodedJwt) {
    Preconditions.checkNotNull(encodedJwt);
    return "ey" + encodedJwt
      .replace("~~", ".ey")
      .replace('~', '.');
  }
}
