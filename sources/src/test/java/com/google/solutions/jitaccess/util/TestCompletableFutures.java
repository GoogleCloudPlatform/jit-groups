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

import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

public class TestCompletableFutures {
  private class CheckedException extends Exception {}
  private static final Executor EXECUTOR = (Runnable r) -> r.run();

  //---------------------------------------------------------------------------
  // supplyAsync.
  //---------------------------------------------------------------------------

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

  //---------------------------------------------------------------------------
  // mapAsync.
  //---------------------------------------------------------------------------

  @Test
  public void mapAsync_whenArgumentsEmpty() throws Exception {
    var future = CompletableFutures.mapAsync(
      arg -> { throw new IllegalStateException(); },
      List.of(),
      EXECUTOR);

    var results = future.get();
    assertEquals(0, results.size());
  }

  @Test
  public void mapAsync_whenAllSucceed() throws Exception {
    var future = CompletableFutures.mapAsync(
      arg -> arg.toUpperCase(),
      List.of("foo", "bar"),
      EXECUTOR);

    var results = future.get();
    assertEquals(
      List.of("FOO", "BAR"),
      results);
  }

  @Test
  public void mapAsync_whenOneFails() throws Exception {
    var future = CompletableFutures.mapAsync(
      arg -> {
        if (arg.isBlank()) {
          throw new IllegalStateException();
        }
        else {
          return arg.toUpperCase();
        }
      },
      List.of("foo", "bar", ""),
      EXECUTOR);

    var exception = assertThrows(
      ExecutionException.class,
      () -> future.get());

    var aggregateException = assertInstanceOf(AggregateException.class, exception.getCause());
    assertEquals(1, aggregateException.getCauses().size());
    assertInstanceOf(IllegalStateException.class, aggregateException.getCause());
    assertInstanceOf(IllegalStateException.class, aggregateException.getCauses().get(0));
  }

  @Test
  public void mapAsync_whenAllFail() throws Exception {
    var future = CompletableFutures.mapAsync(
      arg -> { throw new IllegalStateException(); },
      List.of("foo", "bar"),
      EXECUTOR);

    var exception = assertThrows(
      ExecutionException.class,
      () -> future.get());

    var aggregateException = assertInstanceOf(AggregateException.class, exception.getCause());
    assertEquals(2, aggregateException.getCauses().size());
  }

  //---------------------------------------------------------------------------
  // getOrRethrow.
  //---------------------------------------------------------------------------

  @Test
  public void getOrRethrow_whenFutureThrowsIoException() {
    var future = CompletableFutures.supplyAsync(
      () -> { throw new IOException("IO!"); },
      EXECUTOR);

    assertThrows(
      IOException.class,
      () -> CompletableFutures.getOrRethrow(future));
  }

  @Test
  public void getOrRethrow_whenFutureThrowsAccessException() {
    var future = CompletableFutures.supplyAsync(
      () -> { throw new AccessDeniedException("Access!"); },
      EXECUTOR);

    assertThrows(
      AccessException.class,
      () -> CompletableFutures.getOrRethrow(future));
  }

  @Test
  public void getOrRethrow_whenFutureThrowsOtherException() {
    var future = CompletableFutures.supplyAsync(
      () -> { throw new RuntimeException("Runtime!"); },
      EXECUTOR);

    assertThrows(
      IOException.class,
      () -> CompletableFutures.getOrRethrow(future));
  }
}
