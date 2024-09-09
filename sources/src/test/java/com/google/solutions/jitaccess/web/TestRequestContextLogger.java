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

import com.google.solutions.jitaccess.catalog.auth.Directory;
import com.google.solutions.jitaccess.catalog.auth.EndUserId;
import com.google.solutions.jitaccess.catalog.auth.SubjectResolver;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRequestContextLogger {

  @Test
  public void info_whenTraceIdAndUserIdSet() {
    var buffer = new StringBuilder();
    var requestContext = new RequestContext(Mockito.mock(SubjectResolver.class));
    requestContext.initialize("GET", "/", "trace-1");
    requestContext.authenticate(
      new EndUserId("id"),
      Directory.CONSUMER,
      new IapDevice("device-id", List.of()));
    var logger = new RequestContextLogger(buffer, requestContext);

    logger.info("event-1", "message-1");

    assertEquals(
      "{\"severity\":\"INFO\",\"logging.googleapis.com/labels\":" +
        "{\"auth/device_id\":\"device-id\",\"event/id\":\"event-1\"," +
        "\"auth/user_id\":\"id\",\"event/type\":\"operational\"," +
        "\"auth/directory\":\"CONSUMER\"," +
        "\"auth/device_access_levels\":\"\",\"request/path\":\"/\"," +
        "\"request/method\":\"GET\"},\"message\":\"message-1\",\"logging.googleapis.com/trace\":\"trace-1\"}\n",
      buffer.toString());
  }

  @Test
  public void info_whenTraceIdAndAccessLevelsSet() {
    var buffer = new StringBuilder();
    var requestContext = new RequestContext(Mockito.mock(SubjectResolver.class));
    requestContext.initialize("GET", "/", "trace-1");
    requestContext.authenticate(
      new EndUserId("id"),
      Directory.CONSUMER,
      new IapDevice("device-id", List.of("level-1", "level-2")));
    var logger = new RequestContextLogger(buffer, requestContext);

    logger.info("event-1", "message-1");

    assertEquals(
      "{\"severity\":\"INFO\",\"logging.googleapis.com/labels\":" +
        "{\"auth/device_id\":\"device-id\",\"event/id\":\"event-1\"," +
        "\"auth/user_id\":\"id\",\"event/type\":\"operational\"," +
        "\"auth/directory\":\"CONSUMER\"," +
        "\"auth/device_access_levels\":\"level-1, level-2\"," +
        "\"request/path\":\"/\",\"request/method\":\"GET\"}," +
        "\"message\":\"message-1\",\"logging.googleapis.com/trace\":\"trace-1\"}\n",
      buffer.toString());
  }

  @Test
  public void error_whenNotAuthenticated() {
    var buffer = new StringBuilder();
    var requestContext = new RequestContext(Mockito.mock(SubjectResolver.class));
    var logger = new RequestContextLogger(buffer, requestContext);
    logger.error("event-1", "message-1");

    assertEquals(
      "{\"severity\":\"ERROR\",\"logging.googleapis.com/labels\":{\"event/id\":\"event-1\"," +
        "\"event/type\":\"operational\"}," +
        "\"message\":\"message-1\"}\n",
      buffer.toString());
  }
}