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

package com.google.solutions.jitaccess.web;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.solutions.jitaccess.core.data.DeviceInfo;
import com.google.solutions.jitaccess.core.data.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IapAssertion {
  private final JsonWebToken.Payload payload;

  public IapAssertion(JsonWebToken.Payload payload) {
    this.payload = payload;
  }

  public IapAssertion(JsonWebSignature payload) {
    this(payload.getPayload());
  }

  /**
   * Extract user information
   */
  public UserId getUserId() {
    return new UserId(
      this.payload.get("sub").toString(),
      this.payload.get("email").toString());
  }

  /**
   * Extract device information (if available)
   */
  public DeviceInfo getDeviceInfo() {
    String deviceId = "unknown";
    List<String> accessLevels = List.of();

    if (this.payload.containsKey("google")) {
      var googleClaim = (Map<?, ?>) this.payload.get("google");

      if (googleClaim.containsKey("device_id")) {
        deviceId = googleClaim.get("device_id").toString();
      }

      if (googleClaim.containsKey("access_levels")) {
        accessLevels = ((Collection<?>) googleClaim.get("access_levels"))
          .stream()
          .map(Object::toString)
          .collect(Collectors.toList());
      }
    }

    return new DeviceInfo(deviceId, accessLevels);
  }
}
