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

package com.google.solutions.jitaccess.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Represents one or more exceptions that occurred during an asynchronous operation.
 */
public class AggregateException extends ExecutionException {
  private final @NotNull List<Exception> exceptions;
  private Throwable cause;

  public AggregateException(@NotNull Collection<Exception> exceptions) {
    this.exceptions = List.copyOf(exceptions);
  }

  public AggregateException(Exception... exceptions) {
    this.exceptions = List.of(exceptions);
  }

  @Override
  public synchronized @Nullable Throwable getCause() {
    if (this.cause == null) {
      if (this.exceptions.isEmpty()) {
        this.cause = null;
      }
      else if (this.exceptions.size() == 1) {
        this.cause = this.exceptions.get(0);
      }
      else {
        //
        // Use the first exception with all other exceptions
        // added as "suppressed". That way, we don't lose
        // track of the other exceptions.
        //
        // Make sure we only do this once, even when getCause
        // is invoked multiple times.
        //
        this.cause = this.exceptions.get(0);
        this.exceptions.stream()
          .skip(1)
          .forEach(this.cause::addSuppressed);
      }
    }

    return this.cause;
  }

  public @NotNull List<Exception> getCauses() {
    return List.copyOf(this.exceptions);
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
