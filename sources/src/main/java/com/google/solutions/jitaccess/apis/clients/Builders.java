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

package com.google.solutions.jitaccess.apis.clients;

import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClient;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.Credentials;
import com.google.solutions.jitaccess.ApplicationVersion;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Helper class for initializing API builders.
 */
abstract class Builders {
  private static final @NotNull GsonFactory GSON = new GsonFactory();

  /**
   * Create a new Builder that uses default transport and authentication settings.
   */
  public static @NotNull <TBuilder extends AbstractGoogleJsonClient.Builder> TBuilder newBuilder(
    @NotNull BuilderConstructor<TBuilder> newBuilder,
    @NotNull Credentials credentials,
    @NotNull HttpTransport.Options httpOptions
  ) throws IOException {
    try {
      var builder = newBuilder.create(
        HttpTransport.newTransport(),
        GSON,
        HttpTransport.newAuthenticatingRequestInitializer(credentials, httpOptions));
      builder.setApplicationName(ApplicationVersion.USER_AGENT);
      return builder;
    }
    catch (GeneralSecurityException e) {
      throw new IOException("Creating a client failed", e);
    }
  }

  /**
   * Constructor for the respective Builder class.
   */
  @FunctionalInterface
  public interface BuilderConstructor<TBuilder extends AbstractGoogleJsonClient.Builder> {
    TBuilder create(
      @NotNull com.google.api.client.http.HttpTransport transport,
      @NotNull JsonFactory jsonFactory,
      @NotNull com.google.api.client.http.HttpRequestInitializer httpRequestInitializer);
  }
}
