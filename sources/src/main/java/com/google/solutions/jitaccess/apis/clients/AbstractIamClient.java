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
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.SetIamPolicyRequest;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Base class for 'IAM Meta API' clients, i.e. clients that modify IAM policies
 * using resource-specific getIamPolicy and setIamPolicy methods.
 */
public abstract class AbstractIamClient {
  /**
   * Create a new, API-specific client.
   */
  abstract @NotNull AbstractGoogleJsonClient createClient() throws IOException;

  /**
   * Modify a resource's IAM policy using the optimistic
   * concurrency control-mechanism.
   */
  protected void modifyIamPolicy(
    @NotNull String fullResourcePath,
    @NotNull Consumer<Policy> modify,
    @NotNull String requestReason
  ) throws AccessException, IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Read IAM policy of resource.
   */
  protected GetIamPolicy getIamPolicy(
    @NotNull String fullResourcePath,
    @NotNull GetIamPolicyRequest content
  ) throws IOException {
    return new GetIamPolicy(createClient(), fullResourcePath, content);
  }

  /**
   * Set IAM policy on resource.
   */
  protected SetIamPolicy setIamPolicy(
    @NotNull String fullResourcePath,
    @NotNull SetIamPolicyRequest content
  ) throws IOException {
    return new SetIamPolicy(createClient(), fullResourcePath, content);
  }

  //---------------------------------------------------------------------------
  // Request classes.
  //---------------------------------------------------------------------------

  class GetIamPolicy extends AbstractGoogleJsonClientRequest<Policy> {
    protected GetIamPolicy(
      @NotNull AbstractGoogleJsonClient client,
      @NotNull String fullResourcePath,
      @NotNull GetIamPolicyRequest content
    ) {
      super(
        client,
        "POST",
        fullResourcePath + ":getIamPolicy",
        content,
        Policy.class);
    }
  }

  class SetIamPolicy extends AbstractGoogleJsonClientRequest<Policy> {
    protected SetIamPolicy(
      @NotNull AbstractGoogleJsonClient client,
      @NotNull String fullResourcePath,
      @NotNull SetIamPolicyRequest content
    ) {
      super(
        client,
        "POST",
        fullResourcePath + ":setIamPolicy",
        content,
        Policy.class);
    }
  }
}