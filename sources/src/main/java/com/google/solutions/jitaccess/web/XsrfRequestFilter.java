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

package com.google.solutions.jitaccess.web;

import com.google.solutions.jitaccess.core.AccessDeniedException;

import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Require requests to have a special header to prevent against
 * unauthorized cross-site requests (XSRF).
 */
@Dependent
@Provider
@Priority(Priorities.AUTHENTICATION - 200)
public class XsrfRequestFilter implements ContainerRequestFilter {

  public static final String XSRF_HEADER_NAME = "X-JITACCESS";
  public static final String XSRF_HEADER_VALUE = "1";

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    //
    // Verify that the request contains a special header. Trying to inject
    // that header from a different site would trigger a CORS check, which
    // we'd deny.
    //
    if (!XSRF_HEADER_VALUE.equals(containerRequestContext.getHeaderString(XSRF_HEADER_NAME))) {
      containerRequestContext.abortWith(
        Response
          .status(400, "Invalid request")
          .entity(new ExceptionMappers.ErrorEntity(
            new AccessDeniedException("Missing header: " + XSRF_HEADER_NAME)))
          .build());
    }
  }
}
