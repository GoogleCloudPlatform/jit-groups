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

package com.google.solutions.jitaccess.catalog.policy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A variant property. Properties are used to manage input for
 * evaluating constraints.
 *
 * Properties are typed, but support conversion from and to String.
 */
public interface Property {
  /**
   * Unique name of the property.
   */
  @NotNull String name();

  /**
   * Display name of the property.
   */
  @NotNull String displayName();

  /**
   * Type of the property.
   */
  @NotNull Class<?> type();

  /**
   * Minimum allowed value, if constrained.
   */
  @NotNull Optional<String> minInclusive();

  /**
   * Maximum allowed value, if constrained.
   */
  @NotNull Optional<String> maxInclusive();

  /**
   * Indicates whether a non-null value is required.
   */
  boolean isRequired();

  /**
   * Parse string value and assign to property.
   */
  void set(@Nullable String s);

  /**
   * Read assigned value, converted to a string.
   */
  @Nullable String get();
}
