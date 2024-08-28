//
// Copyright 2021 Google LLC
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
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.SetIamPolicyRequest;
import com.google.auth.oauth2.GoogleCredentials;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Generic client for IAM meta APIs, i.e. API that expose
 * getIamPolicy and setIamPolicy methods.
 */
public class MetaIamClient {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private final @NotNull GoogleCredentials credentials;
  private final @NotNull HttpTransport.Options httpOptions;

  public MetaIamClient(
    @NotNull GoogleCredentials credentials,
    @NotNull HttpTransport.Options httpOptions
  ) {
    this.credentials = credentials;
    this.httpOptions = httpOptions;
  }

  private @NotNull MetaApi createClient(
    @NotNull String endpoint
  ) throws IOException {
    return Builders
      .newBuilder((t, j, h) -> new MetaApi.Builder(endpoint, t, j, h), this.credentials, this.httpOptions)
      .build();
  }

  /**
   * Generic client that works for any API that exposes
   * getIamPolicy and setIamPolicy methods.
   */
  static class MetaApi extends AbstractGoogleJsonClient {
    MetaApi(Builder builder) {
      super(builder);
    }

    /**
     * Read IAM policy of resource.
     */
    public GetIamPolicy getIamPolicy(
      @NotNull String fullResourcePath,
      @NotNull GetIamPolicyRequest content
    ) throws IOException {
      var result = new GetIamPolicy(fullResourcePath, content);
      MetaApi.this.initialize(result);
      return result;
    }

    /**
     * Set IAM policy on resource.
     */
    public SetIamPolicy setIamPolicy(
      @NotNull String fullResourcePath,
      @NotNull SetIamPolicyRequest content
    ) throws IOException {
      var result = new SetIamPolicy(fullResourcePath, content);
      MetaApi.this.initialize(result);
      return result;
    }

    class GetIamPolicy extends AbstractGoogleJsonClientRequest<Policy> {
      protected GetIamPolicy(
        @NotNull String fullResourcePath,
        @NotNull GetIamPolicyRequest content
      ) {
        super(
          MetaApi.this,
          "POST",
          fullResourcePath + ":getIamPolicy",
          content,
          Policy.class);
      }
    }

    class SetIamPolicy extends AbstractGoogleJsonClientRequest<Policy> {
      protected SetIamPolicy(
        @NotNull String fullResourcePath,
        @NotNull SetIamPolicyRequest content
      ) {
        super(
          MetaApi.this,
          "POST",
          fullResourcePath + ":setIamPolicy",
          content,
          Policy.class);
      }
    }

    public static final class Builder extends AbstractGoogleJsonClient.Builder {
      public Builder(
        @NotNull String endpoint,
        @NotNull com.google.api.client.http.HttpTransport transport,
        @NotNull JsonFactory jsonFactory,
        @NotNull HttpRequestInitializer httpRequestInitializer
      ) {
        super(transport, jsonFactory, endpoint, "", httpRequestInitializer, false);
      }

      @Override
      public MetaApi build() {
        return new MetaApi(this);
      }
    }
  }
}