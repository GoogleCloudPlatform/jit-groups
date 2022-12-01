//
// Copyright 2021 Google LLC
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

package com.google.solutions.jitaccess.core.adapters;

import com.google.solutions.jitaccess.core.data.DeviceInfo;
import com.google.solutions.jitaccess.core.data.UserId;
import com.google.solutions.jitaccess.core.data.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestLogAdapter {
  @Test
  public void whenTraceIdAndUserIdSet_ThenWriteLogIncludesFields() {
    var buffer = new StringBuilder();
    var adapter = new LogAdapter(buffer);
    adapter.setTraceId("trace-1");
    adapter.setPrincipal(
      new UserPrincipal() {
        @Override
        public UserId getId() {
          return new UserId("id", "email");
        }

        @Override
        public DeviceInfo getDevice() {
          return new DeviceInfo("device-id", List.of());
        }

        @Override
        public String getName() {
          return "-";
        }
      });

    adapter.newInfoEntry("event-1", "message-1").write();

    assertEquals(
      "{\"severity\":\"INFO\",\"message\":\"message-1\",\"logging.googleapis.com/labels\":" +
        "{\"device_id\":\"device-id\",\"user_id\":\"id\",\"event\":\"event-1\",\"user\":" +
        "\"email\",\"device_access_levels\":\"\"},\"logging.googleapis.com/trace\":\"trace-1\"}\n",
      buffer.toString());
  }

  @Test
  public void whenTraceIdAndAccessLevelsSet_ThenWriteLogIncludesFields() {
    var buffer = new StringBuilder();
    var adapter = new LogAdapter(buffer);
    adapter.setTraceId("trace-1");
    adapter.setPrincipal(
      new UserPrincipal() {
        @Override
        public UserId getId() {
          return new UserId("id", "email");
        }

        @Override
        public DeviceInfo getDevice() {
          return new DeviceInfo("device-id", List.of("level-1", "level-2"));
        }

        @Override
        public String getName() {
          return "-";
        }
      });

    adapter.newInfoEntry("event-1", "message-1").write();

    assertEquals(
      "{\"severity\":\"INFO\",\"message\":\"message-1\",\"logging.googleapis.com/labels\":" +
        "{\"device_id\":\"device-id\",\"user_id\":\"id\",\"event\":\"event-1\",\"user\":" +
        "\"email\",\"device_access_levels\":\"level-1, level-2\"}," +
        "\"logging.googleapis.com/trace\":\"trace-1\"}\n",
      buffer.toString());
  }

  @Test
  public void whenTraceIdAndPrincipalNotSet_ThenWriteLogSucceeds() {
    var buffer = new StringBuilder();
    var adapter = new LogAdapter(buffer);
    adapter.newErrorEntry("event-1", "message-1").write();

    assertEquals(
      "{\"severity\":\"ERROR\",\"message\":\"message-1\",\"logging.googleapis.com/labels\"" +
        ":{\"event\":\"event-1\"},\"logging.googleapis.com/trace\":null}\n",
      buffer.toString());
  }
}
