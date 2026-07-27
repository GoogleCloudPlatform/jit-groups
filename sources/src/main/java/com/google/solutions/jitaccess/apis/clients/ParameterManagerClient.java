//
// Copyright 2026 Google LLC
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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.parametermanager.v1.ParameterManager;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.RegionId;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Client for the Parameter Manager API.
 */
public class ParameterManagerClient {
  private static final String PARAMETER_CHARSET = "UTF-8";
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private final @NotNull GoogleCredentials credentials;
  private final @NotNull RegionId regionId;
  private final @NotNull HttpTransport.Options httpOptions;

  public ParameterManagerClient(
    @NotNull GoogleCredentials credentials,
    @NotNull RegionId regionId,
    @NotNull HttpTransport.Options httpOptions
  ) {
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(regionId, "regionId");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.credentials = credentials;
    this.regionId = regionId;
    this.httpOptions = httpOptions;
  }

  private @NotNull ParameterManager createClient() throws IOException {
    //
    // NB. Regional parameters are only accessible via the REP endpoint.
    //
    return Builders
      .newBuilder(ParameterManager.Builder::new, this.credentials, this.httpOptions)
      .setRootUrl(String.format("https://parametermanager.%s.rep.googleapis.com/", this.regionId.id()))
      .build();
  }

  /**
   * Access a rendered parameter version
   * @param parameterPath resource path, in the format projects/x/locations/q/parameter/y/versions/z
   */
  public @Nullable String render(
    String parameterPath
  ) throws AccessException, IOException {
    try {
      var payload = createClient()
        .projects()
        .locations()
        .parameters()
        .versions()
        .render(parameterPath)
        .execute()
        .getPayload();

      if (payload == null) {
        return null;
      }

      var payloadData = payload.decodeData();
      if (payloadData == null) {
        return null;
      }
      else {
        return new String(payloadData, PARAMETER_CHARSET);
      }
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(
            String.format("Access to parameter '%s' was denied", parameterPath), e);
        case 404:
          throw new ResourceNotFoundException(
            String.format("The parameter '%s' does not exist", parameterPath), e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }
}
