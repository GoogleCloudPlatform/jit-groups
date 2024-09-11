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

import com.google.common.util.concurrent.UncheckedExecutionException;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Lazily initializes an object
 */
public abstract class Lazy<T> implements Supplier<T> {
  protected final @NotNull AtomicReference<T> cached;

  private Lazy() {
    this.cached = new AtomicReference<>(null);
  }

  /**
   * Initialize using an opportunistic approach in which the initializer
   * might be run more than once.
   *
   * @throws UncheckedExecutionException if the initializer fails.
   */
  public static @NotNull <T> Lazy<T> initializeOpportunistically(
    @NotNull Callable<T> initialize
  ) {
    return new Lazy<>() {
      @Override
      public T get() {
        var obj = cached.get();
        if (obj != null) {
          return obj;
        }
        else {
          //
          // Initialize a new instance and try to set it as
          // cached reference. There might be another thread
          // doing the same.
          //
          try {
            obj = initialize.call();
          }
          catch (Exception e) {
            throw new UncheckedExecutionException(e);
          }

          if (cached.compareAndSet(null, obj)) {
            //
            // We won the race, use this instance.
            //
            return obj;
          }
          else {
            //
            // Another thread was faster.
            //
            var existing = this.cached.get();
            assert existing != null;
            return existing;
          }
        }
      }
    };
  }
}
