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

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

public class TestCompletableFutures {
  private class CheckedException extends Exception {}
  private static final Executor EXECUTOR = (Runnable r) -> r.run();

  @Test
  public void supplyAsync_whenCallableReturnsValue() throws Exception {
    var future = CompletableFutures.supplyAsync(
      () -> "test",
      EXECUTOR);
    assertEquals("test", future.get());
  }

  @Test
  public void supplyAsync_whenCallableThrowsCheckedException() throws Exception {
    var future = CompletableFutures.supplyAsync(
      () -> { throw new CheckedException(); },
      EXECUTOR);

    var exception = assertThrows(
      ExecutionException.class,
      () -> future.get());
    assertInstanceOf(CheckedException.class, exception.getCause());
    assertInstanceOf(CheckedException.class, Exceptions.unwrap(exception));
  }

  @Test
  public void supplyAsync_whenCallableThrowsRuntimeException() throws Exception {
    var future = CompletableFutures.supplyAsync(
      () -> { throw new IllegalArgumentException(); },
      EXECUTOR);

    var exception = assertThrows(
      ExecutionException.class,
      () -> future.get());
    assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    assertInstanceOf(IllegalArgumentException.class, Exceptions.unwrap(exception));
  }
}
