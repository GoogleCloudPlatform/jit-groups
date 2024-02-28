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

package com.google.solutions.jitaccess.apis;

import org.jetbrains.annotations.NotNull;

/**
 * Identifier for a Resource Manager resource.
 */
public interface ResourceId {
  /**
   * Type of resource, for example project, folder, organization.
   */
  @NotNull String type();

  /**
   * Unique ID of the resource, without prefix.
   */
  @NotNull String id();

  /**
   * Path, in notation type/id.
   *
   * For example, projects/test-123 folders/234, organizations/345.
   */
  @NotNull String path();
}
