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

import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.apis.clients.AccessDeniedException;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Verifies that requests contain the header and origin IP
 * that are characteristic for requests originated
 * by AppEngine Cron.
 *
 * @see <a href="https://cloud.google.com/appengine/docs/standard/scheduling-jobs-with-cron-yaml#securing_urls_for_cron">...</a>
 */
@Dependent
@Provider
@Priority(Priorities.AUTHENTICATION)
@RequireAppEngineCronRequest
public class RequireAppEngineCronRequestFilter implements ContainerRequestFilter {
  static final String CRON_HEADER = "X-Appengine-Cron";

  @Inject
  Logger logger;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (!"true".equalsIgnoreCase(requestContext.getHeaderString(CRON_HEADER))) {
      this.logger.info(
        EventIds.API_AUTHENTICATE,
        "Request does not originate from AppEngine cron");

      requestContext.abortWith(
        Response
          .status(403, "Forbidden")
          .entity(new ExceptionMappers.ErrorEntity(
            new AccessDeniedException("Missing header: " + CRON_HEADER)))
          .build());
    }
  }
}
