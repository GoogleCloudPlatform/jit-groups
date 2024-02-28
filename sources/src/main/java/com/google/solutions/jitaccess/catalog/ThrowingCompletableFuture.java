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

package com.google.solutions.jitaccess.catalog;

import com.google.solutions.jitaccess.apis.clients.AccessException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Completable future for a supplier that can throw a checked exception.
 */
public class ThrowingCompletableFuture {
  /**
   * Function that can throw a checked exception.
   */
  @FunctionalInterface
  public interface ThrowingSupplier<T> {
    T supply() throws Exception;
  }

  public static <T> @NotNull CompletableFuture<T> submit(
    @NotNull ThrowingSupplier<T> supplier,
    @NotNull Executor executor
  ) {
    var future = new CompletableFuture<T>();
    executor.execute(() -> {
      try {
        future.complete(supplier.supply());
      }
      catch (Exception e) {
        future.completeExceptionally(e);
      }
    });

    return future;
  }

  /**
   * Await a future and rethrow exceptions, unwrapping known exceptions.
   */
  public static <T> T awaitAndRethrow(
    @NotNull CompletableFuture<T> future
  ) throws AccessException, IOException {
    try {
      return future.get();
    }
    catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof AccessException) {
        throw (AccessException)e.getCause().fillInStackTrace();
      }

      if (e.getCause() instanceof IOException) {
        throw (IOException)e.getCause().fillInStackTrace();
      }

      throw new IOException("Awaiting executor tasks failed", e);
    }
  }
}
