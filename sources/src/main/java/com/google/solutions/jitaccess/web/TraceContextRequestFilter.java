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

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.adapters.LogAdapter;

import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;

/**
 * Use AppEngine-specific headers to enrich the log.
 */
@Dependent
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class TraceContextRequestFilter implements ContainerRequestFilter {
  /**
   * Header that contains a unique identifier for the request, cf.
   * https://cloud.google.com/appengine/docs/standard/java11/reference/request-response-headers
   */
  private static final String TRACE_CONTEXT_HEADER_NAME = "X-Cloud-Trace-Context";

  @Inject
  LogAdapter log;

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    Preconditions.checkNotNull(this.log, "log");

    var traceId = containerRequestContext.getHeaderString(TRACE_CONTEXT_HEADER_NAME);
    if (traceId != null && !traceId.isEmpty()) {
      //
      // Associate the trace ID with the current request so that
      // subsequent logs can be correlated.
      //
      this.log.setTraceId(traceId);
    }
  }
}
