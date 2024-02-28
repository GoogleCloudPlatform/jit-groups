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

package com.google.solutions.jitaccess.catalog.auth;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Information about a user's device and the ACM access
 * levels it satisfies.
 */
public interface Device {
  /**
   * @return the device's ID.
   */
  @NotNull String deviceId();

  /**
   * @return list of satisfied ACM access levels.
   */
  @NotNull Collection<String> accessLevels();
}
