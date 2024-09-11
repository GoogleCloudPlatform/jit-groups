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

package com.google.solutions.jitaccess.auth;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.apis.Logger;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Subject resolver that caches results.
 */
@Singleton
public class CachedSubjectResolver extends SubjectResolver {
  private final @NotNull LoadingCache<EndUserId, Set<Principal>> cache;

  public CachedSubjectResolver(
    @NotNull CloudIdentityGroupsClient groupsClient,
    @NotNull GroupMapping groupMapping,
    @NotNull Executor executor,
    @NotNull Logger logger,
    @NotNull Options options
  ) {
    super(groupsClient, groupMapping, options.internalDirectory, executor, logger);

    this.cache =  CacheBuilder.newBuilder()
      .expireAfterWrite(options.cacheDuration)
      .build(new CacheLoader<>() {

        @Override
        public @NotNull Set<Principal> load(@NotNull EndUserId userId) throws Exception {
          return CachedSubjectResolver.super.resolveGroupPrincipals(userId);
        }
      });
  }

  @Override
  protected @NotNull Set<Principal> resolveGroupPrincipals(
    @NotNull EndUserId user
  ) throws AccessException, IOException {
    try {
      return this.cache.getUnchecked(user);
    }
    catch (UncheckedExecutionException e) {
      if (e.getCause() instanceof AccessException accessException) {
        throw (AccessException)accessException.fillInStackTrace();
      }
      else if (e.getCause() instanceof IOException ioException) {
        throw (IOException)ioException.fillInStackTrace();
      }
      else {
        throw (UncheckedExecutionException)e.fillInStackTrace();
      }
    }
  }

  /**
   * Constructor options, injectable using CDI.
   */
  public record Options(
    @NotNull Duration cacheDuration,
    @NotNull Directory internalDirectory
  ) {}
}
