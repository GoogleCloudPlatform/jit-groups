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

package com.google.solutions.jitaccess.common.cel;

import com.google.solutions.jitaccess.common.cel.InvalidExpressionException;
import com.google.solutions.jitaccess.common.cel.StringTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestStringTemplate {
  //---------------------------------------------------------------------------
  // evaluate.
  //---------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "",
    "{test}",
    "}}test{{",
    "test{{",
    "test{{} }"
  })
  public void evaluate_whenStringHasNoEmbeddedExpressions(String s) throws Exception {
    var template = new StringTemplate(s);
    assertEquals(s, template.evaluate());
  }

  @Test
  public void evaluate_whenStringHasMultipleEmbeddedExpressions() throws Exception {
    var template = new StringTemplate(
      "This is a {{ context.x }} {{context.y\t\n\r}}!");
    template.addContext("context")
      .set("x", "simple")
      .set("y", "test");
    assertEquals("This is a simple test!", template.evaluate());
  }

  @Test
  public void evaluate_whenExpressionPerformsAddition() throws Exception {
    var template = new StringTemplate(
      "1 + 1 = {{ 1+1 }}!");
    assertEquals("1 + 1 = 2!", template.evaluate());
  }

  @Test
  public void evaluate_whenExpressionIsInvalid() throws Exception {
    var template = new StringTemplate(
      "{{ unknown }}!");

    var e = assertThrows(
      InvalidExpressionException.class,
      () -> template.evaluate());

    assertEquals("The CEL expression 'unknown' is invalid", e.getMessage());
  }

  //---------------------------------------------------------------------------
  // toString.
  //---------------------------------------------------------------------------

  @Test
  public void toString_returnTemplate() {
    assertEquals("template", new StringTemplate("template").toString());
  }
}
