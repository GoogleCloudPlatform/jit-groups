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

package com.google.solutions.jitaccess.core.data;

import com.google.common.base.Preconditions;

import java.util.List;
import java.util.Objects;

/**
 * Information about the device of a user.
 */
public class DeviceInfo {
  public static final DeviceInfo UNKNOWN = new DeviceInfo("unknown", List.of());
  private final String deviceId;
  private final List<String> accessLevels;

  public DeviceInfo(
    String deviceId,
    List<String> accessLevels
  ) {
    Preconditions.checkNotNull(deviceId, "deviceId");
    Preconditions.checkNotNull(accessLevels, "accessLevels");

    this.deviceId = deviceId;
    this.accessLevels = accessLevels;
  }

  public String getDeviceId() {
    return this.deviceId;
  }

  public List<String> getAccessLevels() {
    return accessLevels;
  }

  @Override
  public String toString() {
    return this.deviceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    var that = (DeviceInfo) o;
    return deviceId.equals(that.deviceId) && accessLevels.equals(that.accessLevels);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deviceId, accessLevels);
  }
}
