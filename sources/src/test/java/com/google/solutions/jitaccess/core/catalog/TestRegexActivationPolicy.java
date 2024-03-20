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

import com.google.solutions.jitaccess.core.auth.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestRegexActivationPolicy {

  private static final UserId SAMPLE_USER = new UserId("user@example.com");

  // -------------------------------------------------------------------------
  // checkJustification.
  // -------------------------------------------------------------------------

  @Test
  public void whenJustificationNullOrEmpty_ThenCheckJustificationThrowsException() {
    var policy = new RegexJustificationPolicy(new RegexJustificationPolicy.Options(
      "hint",
      Pattern.compile(".*")
    ));

    assertThrows(
      InvalidJustificationException.class,
      () -> policy.checkJustification(SAMPLE_USER, null));
    assertThrows(
      InvalidJustificationException.class,
      () -> policy.checkJustification(SAMPLE_USER, ""));
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "a", "b/a", "b/"})
  public void whenJustificationDoesNotMatchRegex_ThenCheckJustificationThrowsException(
    String value
  ) {
    var policy = new RegexJustificationPolicy(new RegexJustificationPolicy.Options(
      "hint",
      Pattern.compile("^b/(\\d+)$")
    ));

    assertThrows(
      InvalidJustificationException.class,
      () -> policy.checkJustification(SAMPLE_USER, value));
  }

  @Test
  public void whenJustificationMatchesRegex_ThenCheckJustificationReturns() throws Exception {
    var policy = new RegexJustificationPolicy(new RegexJustificationPolicy.Options(
      "hint",
      Pattern.compile("^b/(\\d+)$")
    ));

    policy.checkJustification(SAMPLE_USER, "b/1");
  }
}
