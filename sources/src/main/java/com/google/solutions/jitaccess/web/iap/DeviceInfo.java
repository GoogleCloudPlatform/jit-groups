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

package com.google.solutions.jitaccess.web.iap;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Information about the device of a user.
 */
public record DeviceInfo(String deviceId, List<String> accessLevels) {
  public static final DeviceInfo UNKNOWN = new DeviceInfo("unknown", List.of());

  public DeviceInfo {
    Preconditions.checkNotNull(deviceId, "deviceId");
    Preconditions.checkNotNull(accessLevels, "accessLevels");
  }

  @Override
  public String toString() {
    return this.deviceId;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    var that = (DeviceInfo) o;
    return deviceId.equals(that.deviceId) && accessLevels.equals(that.accessLevels);
  }
}
