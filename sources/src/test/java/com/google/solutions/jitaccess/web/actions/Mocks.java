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

package com.google.solutions.jitaccess.web.actions;

import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import com.google.solutions.jitaccess.web.iap.DeviceInfo;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.specimpl.ResteasyUriInfo;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

abstract class Mocks {
  static NotificationService createNotificationServiceMock(boolean canSend) {
    var service = Mockito.mock(NotificationService.class);
    when(service.canSendNotifications()).thenReturn(canSend);
    return service;
  }

  static MpaProjectRoleCatalog createMpaProjectRoleCatalogMock() {
    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (catalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));
    return catalog;
  }

  static UriInfo createUriInfoMock() {
    return new ResteasyUriInfo("http://example.com/", "/");
  }

  static RuntimeEnvironment createRuntimeEnvironmentMock() {
    var environment = Mockito.mock(RuntimeEnvironment.class);
    when(environment.createAbsoluteUriBuilder(any(UriInfo.class)))
      .thenReturn(UriBuilder.fromUri("https://example.com/"));
    return environment;
  }

  static IapPrincipal createIapPrincipalMock(@NotNull UserId userId) {
    return new IapPrincipal() {
      @Override
      public UserId email() {
        return userId;
      }

      @Override
      public String subjectId() {
        return "mock";
      }

      @Override
      public DeviceInfo device() {
        return DeviceInfo.UNKNOWN;
      }

      @Override
      public String getName() {
        return "mock@example.com";
      }
    };
  }
}
