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

package com.google.solutions.jitaccess.util;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * An Optional that is either empty or not, but doesn't carry
 * an actual value.
 */
public class NullaryOptional {
  private final boolean present;

  private NullaryOptional(boolean present) {
    this.present = present;
  }

  public static NullaryOptional ifTrue(boolean condition) {
    return new NullaryOptional(condition);
  }

  public <U> @NotNull Optional<U> map(@NotNull Supplier<U> mapper) {
    if (this.present) {
      var value = mapper.get();
      return value == null ? Optional.empty() : Optional.of(value);
    }
    else {
      return Optional.empty();
    }
  }

  public <U> @NotNull Optional<U> flatMap(@NotNull Supplier<Optional<U>> mapper) {
    if (this.present) {
      return mapper.get();
    }
    else {
      return Optional.empty();
    }
  }

  public boolean isPresent() {
    return this.present;
  }

  public boolean isEmpty() {
    return !this.present;
  }
}