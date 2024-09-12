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

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.checkerframework.checker.units.qual.N;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Lazily initializes an object
 */
public abstract class Lazy<T> implements Supplier<T>, Future<T> {
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
  public static @NotNull <T> Lazy<T> opportunistic(
    @NotNull Callable<T> initialize
  ) {
    return new Lazy<>() {
      @Override
      public @NotNull T get() {
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

  /**
   * Initialize using a pessimistic approach that runs the initializer
   * at most once.
   *
   * @throws UncheckedExecutionException if the initializer fails.
   */
  public static @NotNull <T> Lazy<T> pessimistic(
    @NotNull Callable<T> initialize
  ) {
    return new Lazy<>() {
      private Exception initializationException = null;

      @Override
      public @NotNull T get() {
        var value = this.cached.get();

        if (value == null) {
          //
          // Initialize a new instance.
          //
          synchronized (this.cached) {
            if (this.initializationException != null) {
              //
              // Another thread tried initializing before,
              // but failed.
              //
              throw new UncheckedExecutionException(this.initializationException);
            }
            else if ((value = this.cached.get()) != null) {
              //
              // Another thread acquired the lock before us and
              // completed initialization already.
              //
            }
            else {
              //
              // Try to initialize.
              //
              try {
                value = initialize.call();
                Preconditions.checkNotNull(value);

                this.cached.set(value);
              }
              catch (Exception e) {
                this.initializationException = e;
                throw new UncheckedExecutionException(e);
              }
            }
          }

          assert value != null;
        }

        return value;
      }
    };
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public boolean isDone() {
    return this.cached.get() != null;
  }

  @Override
  public @NotNull T get(long timeout, @NotNull TimeUnit unit) {
    return get();
  }

  @Override
  public abstract @NotNull T get();
}
