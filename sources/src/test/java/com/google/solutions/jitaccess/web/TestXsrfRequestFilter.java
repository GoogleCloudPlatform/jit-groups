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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.ws.rs.container.ContainerRequestContext;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TestXsrfRequestFilter {
  @Test()
  public void whenHeaderMissing_ThenRequestIsAborted() {
    var filter = new XsrfRequestFilter();

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
    when(request.getHeaderString(anyString())).thenReturn(null);

    filter.filter(request);

    verify(request, times(1)).abortWith(Mockito.any());
  }

  @Test()
  public void whenHeaderHasWrongValue_ThenRequestIsAborted() {
    var filter = new XsrfRequestFilter();

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
    when(request.getHeaderString(XsrfRequestFilter.XSRF_HEADER_NAME)).thenReturn("test");

    filter.filter(request);

    verify(request, times(1)).abortWith(Mockito.any());
  }

  @Test()
  public void whenHeaderHasCorrectValue_ThenRequestProceeds() {
    var filter = new XsrfRequestFilter();

    ContainerRequestContext request = Mockito.mock(ContainerRequestContext.class);
    when(request.getHeaderString(XsrfRequestFilter.XSRF_HEADER_NAME))
      .thenReturn(XsrfRequestFilter.XSRF_HEADER_VALUE);

    filter.filter(request);

    verify(request, times(0)).abortWith(Mockito.any());
  }
}
