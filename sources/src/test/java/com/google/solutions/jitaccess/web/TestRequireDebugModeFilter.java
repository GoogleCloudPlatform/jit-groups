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

import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

public class TestRequireDebugModeFilter {
  @Test()
  public void filter_debugModeOn() {
    var filter = new RequireDebugModeFilter();
    filter.application = Mockito.mock(Application.class);
    when(filter.application.isDebugModeEnabled())
      .thenReturn(true);

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);

    filter.filter(request);

    verify(request, times(0)).abortWith(any());
  }

  @Test()
  public void filter_debugModeOff() {
    var filter = new RequireDebugModeFilter();
    filter.application = Mockito.mock(Application.class);
    when(filter.application.isDebugModeEnabled())
      .thenReturn(false);

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);

    filter.filter(request);

    verify(request, times(1)).abortWith(argThat(r -> r.getStatus() == 403));
  }
}
