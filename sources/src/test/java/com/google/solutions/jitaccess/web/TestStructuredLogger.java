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

package com.google.solutions.jitaccess.web;

import com.google.solutions.jitaccess.apis.StructuredLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestStructuredLogger {
  // -------------------------------------------------------------------------
  // info.
  // -------------------------------------------------------------------------
  @Test
  public void info_whenMessageHasNoArguments() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.info("E1", "message");

    assertEquals(
      "{\"severity\":\"INFO\",\"logging.googleapis.com/labels\":{\"event/id\":\"E1\"," +
        "\"event/type\":\"operational\"},\"message\":\"message\"}\n",
      buffer.toString());
  }

  @Test
  public void info_whenMessageHasArguments() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.info("E1", "s=%s, d=%d", "test", 1);

    assertEquals(
      "{\"severity\":\"INFO\",\"logging.googleapis.com/labels\":{\"event/id\":\"E1\"," +
        "\"event/type\":\"operational\"},\"message\":\"s=test, d=1\"}\n",
      buffer.toString());
  }

  // -------------------------------------------------------------------------
  // warn.
  // -------------------------------------------------------------------------

  @Test
  public void warn_whenMessageHasNoArguments() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.warn("E1", "message");

    assertEquals(
      "{\"severity\":\"WARN\",\"logging.googleapis.com/labels\":{\"event/id\":\"E1\"," +
        "\"event/type\":\"operational\"},\"message\":\"message\"}\n",
      buffer.toString());
  }

  @Test
  public void warn_whenMessageHasArguments() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.warn("E1", "s=%s, d=%d", "test", 1);

    assertEquals(
      "{\"severity\":\"WARN\",\"logging.googleapis.com/labels\":{\"event/id\":\"E1\"," +
        "\"event/type\":\"operational\"},\"message\":\"s=test, d=1\"}\n",
      buffer.toString());
  }

  @Test
  public void warn_whenExceptionPassed() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.warn("E1", "exception",
      new IllegalStateException("outer-exception",
        new IllegalArgumentException("inner-exception")));

    assertTrue(buffer.toString()
      .contains("exception: outer-exception, caused by IllegalArgumentException: inner-exception"));
  }

  // -------------------------------------------------------------------------
  // error.
  // -------------------------------------------------------------------------

  @Test
  public void error_whenMessageHasNoArguments() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.error("E1", "message");

    assertEquals(
      "{\"severity\":\"ERROR\"," +
        "\"logging.googleapis.com/labels\":{\"event/id\":\"E1\",\"event/type\":\"operational\"}," +
        "\"message\":\"message\"}\n",
      buffer.toString());
  }

  @Test
  public void error_whenMessageHasArguments() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.error("E1", "s=%s, d=%d", "test", 1);

    assertEquals(
      "{\"severity\":\"ERROR\",\"logging.googleapis.com/labels\":{\"event/id\":\"E1\",\"event/type\":\"operational\"}," +
        "\"message\":\"s=test, d=1\"}\n",
      buffer.toString());
  }

  @Test
  public void error_whenExceptionPassed() {
    var buffer = new StringBuilder();
    var logger = new StructuredLogger(buffer) {};
    logger.error("E1", "exception",
      new IllegalStateException("outer-exception",
        new IllegalArgumentException("inner-exception")));

    assertTrue(buffer.toString()
      .contains("exception: outer-exception, caused by IllegalArgumentException: inner-exception"));
  }
}
