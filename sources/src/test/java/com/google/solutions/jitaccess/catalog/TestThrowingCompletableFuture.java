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

import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestThrowingCompletableFuture {

  private static class SynchronousExecutor implements Executor {

    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }

  //---------------------------------------------------------------------------
  // awaitAndRethrow.
  //---------------------------------------------------------------------------

  @Test
  public void awaitAndRethrow_whenFutureThrowsIoException() {
    var future = ThrowingCompletableFuture.<String>submit(
      () -> { throw new IOException("IO!"); },
      new SynchronousExecutor());

    assertThrows(
      IOException.class,
      () -> ThrowingCompletableFuture.awaitAndRethrow(future));
  }

  @Test
  public void awaitAndRethrow_whenFutureThrowsAccessException() {
    var future = ThrowingCompletableFuture.<String>submit(
      () -> { throw new AccessDeniedException("Access!"); },
      new SynchronousExecutor());

    assertThrows(
      AccessException.class,
      () -> ThrowingCompletableFuture.awaitAndRethrow(future));
  }
  @Test
  public void awaitAndRethrow_whenFutureThrowsOtherException() {
    var future = ThrowingCompletableFuture.<String>submit(
      () -> { throw new RuntimeException("Runtime!"); },
      new SynchronousExecutor());

    assertThrows(
      IOException.class,
      () -> ThrowingCompletableFuture.awaitAndRethrow(future));
  }
}
