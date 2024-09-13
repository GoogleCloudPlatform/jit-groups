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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
   * Check if initialization has been performed yet.
   */
  @Override
  public abstract boolean isDone();

  @Override
  public abstract @NotNull T get();

  /**
   * Reset value and reinitialize on next access.
   */
  abstract void reset();

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public @NotNull T get(long timeout, @NotNull TimeUnit unit) {
    return get();
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
    return  new OptimisticLazy<>(initialize);
  }

  /**
   * Initialize using a pessimistic approach that runs the initializer
   * at most once.
   *
   * @throws UncheckedExecutionException if the initializer fails.
   */
  public static @NotNull <T> Lazy<T> initializePessimistically(
    @NotNull Callable<T> initialize
  ) {
    return new PessimisticLazy<>(initialize);
  }

  /**
   * Wrap a Lazy<T> so that the source is being reset automatically
   * after a certain duration elapses, effectively turning the
   * Lazy<T> into a cache.
   */
  public @NotNull Lazy<T> reinitializeAfter(
    @NotNull Duration duration
  ) {
    return new AutoResetLazy<>(this, duration);
  }

  //---------------------------------------------------------------------------
  // Optimistic strategy.
  //---------------------------------------------------------------------------

  private static class OptimisticLazy<T> extends Lazy<T> {
    private final @NotNull AtomicReference<T> cached = new AtomicReference<>(null);
    private final @NotNull Callable<T> initializer;

    public OptimisticLazy(@NotNull Callable<T> initializer) {
      this.initializer = initializer;
    }

    void reset() {
      this.cached.set(null);
    }

    @Override
    public boolean isDone() {
      return this.cached.get() != null;
    }

    @Override
    public @NotNull T get() {
      var obj = this.cached.get();
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
          obj = this.initializer.call();
        }
        catch (Exception e) {
          throw new UncheckedExecutionException(e);
        }

        if (this.cached.compareAndSet(null, obj)) {
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
  }

  //---------------------------------------------------------------------------
  // Pessimistic strategy.
  //---------------------------------------------------------------------------

  private static class PessimisticLazy<T> extends Lazy<T> {
    private final @NotNull AtomicReference<ExceptionOr<T>> cached = new AtomicReference<>(null);
    private final @NotNull Callable<T> initializer;

    public PessimisticLazy(@NotNull Callable<T> initializer) {
      this.initializer = initializer;
    }

    void reset() {
      this.cached.set(null);
    }

    @Override
    public boolean isDone() {
      return this.cached.get() != null;
    }

    @Override
    public @NotNull T get() {
      var cachedValue = this.cached.get();

      if (cachedValue == null) {
        //
        // Initialize a new instance.
        //
        synchronized (this.cached) {
          if ((cachedValue = this.cached.get()) != null) {
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
              var newValue = this.initializer.call();
              Preconditions.checkNotNull(newValue);

              cachedValue = new ExceptionOr<>(newValue, null);
            }
            catch (Exception e) {
              cachedValue = new ExceptionOr<>(null, e);
            }

            this.cached.set(cachedValue);
          }
        }

        assert cachedValue != null;
      }

      if (cachedValue.value != null) {
        return cachedValue.value;
      }
      else {
        assert cachedValue.exception != null;
        throw new UncheckedExecutionException(cachedValue.exception);
      }
    }

    private record ExceptionOr<T>(
      @Nullable T value,
      @Nullable Exception exception
    ) {}
  }

  //---------------------------------------------------------------------------
  // Auto-reset strategy.
  //---------------------------------------------------------------------------

  private static class AutoResetLazy<T> extends Lazy<T> {
    private final @NotNull Duration duration;
    private final @NotNull Lazy<T> source;
    private final @NotNull AtomicLong lastResetTimestamp = new AtomicLong(0);

    private void resetSourceIfDue() {
      var now = System.currentTimeMillis();
      var lastReset = this.lastResetTimestamp.get();
      if (now > lastReset + this.duration.toMillis()) {
        //
        // The value is too old.
        //
        if (this.lastResetTimestamp.compareAndSet(lastReset, now)) {
          reset();
        }
      }
    }

    public AutoResetLazy(
      @NotNull Lazy<T> source,
      @NotNull Duration duration
    ) {
      Preconditions.checkArgument(!duration.isNegative(), "Duration must be positive");

      this.source = source;
      this.duration = duration;
    }

    @Override
    void reset() {
      this.source.reset();
    }

    @Override
    public boolean isDone() {
      resetSourceIfDue();
      return this.source.isDone();
    }

    @Override
    public @NotNull T get() {
      resetSourceIfDue();
      return this.source.get();
    }
  }
}
