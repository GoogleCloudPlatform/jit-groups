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

import com.google.solutions.jitaccess.apis.StructuredLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Logger for operations that run in the context of
 * a user request.
 */
public class RequestContextLogger extends StructuredLogger {
  private final @NotNull RequestContext requestContext;

  RequestContextLogger(
    @NotNull Appendable output,
    @NotNull RequestContext requestContext
  ) {
    super(output);
    this.requestContext = requestContext;
  }

  RequestContextLogger(@NotNull RequestContext requestContext) {
    this(System.out, requestContext);
  }

  @Override
  protected @Nullable String traceId() {
    return this.requestContext.requestTraceId();
  }

  @Override
  protected @NotNull Map<String, String> createLabels(String eventId) {
    var labels = super.createLabels(eventId);

    if (this.requestContext.isAuthenticated()) {
      labels.put("auth/user_id", this.requestContext.user().email);
      labels.put("auth/directory", this.requestContext.subject().directory().toString());
      labels.put("auth/device_id", this.requestContext.device().deviceId());
      labels.put("auth/device_access_levels",
        String.join(", ", this.requestContext.device().accessLevels()));
    }

    labels.put("request/method", this.requestContext.requestMethod());
    labels.put("request/path", this.requestContext.requestPath());

    return labels;
  }
}