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

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents one or more exceptions that occurred during an asynchronous operation.
 */
public class AggregateException extends Exception {
  private final @NotNull Collection<Exception> exceptions;

  public AggregateException(@NotNull Collection<Exception> exceptions) {
    super(exceptions.stream()
      .findFirst()
      .get());
    this.exceptions = exceptions;
  }

  public AggregateException(Exception... exceptions) {
    super(exceptions.length > 0 ? exceptions[0] : null);
    this.exceptions = List.of(exceptions);
  }

  public @NotNull List<Exception> getCauses() {
    return List.copyOf(exceptions);
  }

  @Override
  public String getMessage() {
    return "[" + this.exceptions.stream()
      .map(Exception::getMessage)
      .collect(Collectors.joining(", ")) + "]";
  }

  @Override
  public String toString() {
    return "[" + this.exceptions.stream()
      .map(Exception::toString)
      .collect(Collectors.joining(", ")) + "]";
  }
}
