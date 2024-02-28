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

package com.google.solutions.jitaccess.web;

import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jetbrains.annotations.NotNull;

/**
 * Require application to run in debug mode.
 */
@Dependent
@Provider
@Priority(Priorities.AUTHENTICATION - 300)
@RequireDebugMode
public class RequireDebugModeFilter implements ContainerRequestFilter {
  @Inject
  Application application;

  @Override
  public void filter(@NotNull ContainerRequestContext containerRequestContext) {
    if (!this.application.isDebugModeEnabled()) {
      containerRequestContext.abortWith(
        Response
          .status(403, "Access is denied")
          .entity(new ExceptionMappers.ErrorEntity(
            new AccessDeniedException("Access is denied")))
          .build());
    }
  }
}
