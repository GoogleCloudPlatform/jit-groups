//
// Copyright 2022 Google LLC
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

//TODO: add test for exception mappers

package com.google.solutions.jitaccess.web.rest;

import com.google.auth.oauth2.TokenVerifier;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleActivator;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRole;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import com.google.solutions.jitaccess.web.TokenObfuscator;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TestApiResource {
  private static final UserEmail SAMPLE_USER = new UserEmail("user-1@example.com");
  private static final UserEmail SAMPLE_USER_2 = new UserEmail("user-2@example.com");

  private static final String SAMPLE_TOKEN = "eySAMPLE";
  private static final Pattern DEFAULT_JUSTIFICATION_PATTERN = Pattern.compile("pattern");
  private static final int DEFAULT_MIN_NUMBER_OF_REVIEWERS = 1;
  private static final int DEFAULT_MAX_NUMBER_OF_REVIEWERS = 10;
  private static final int DEFAULT_MAX_NUMBER_OF_ROLES = 3;
  private static final Duration DEFAULT_ACTIVATION_DURATION = Duration.ofMinutes(5);
  private static final TokenSigner.TokenWithExpiry SAMPLE_TOKEN_WITH_EXPIRY =
    new TokenSigner.TokenWithExpiry(
      SAMPLE_TOKEN,
      Instant.now(),
      Instant.now().plusSeconds(10));

  private ApiResource resource;
  private NotificationService notificationService;

  @BeforeEach
  public void before() {
    this.resource = new ApiResource();
    this.resource.options = new ApiResource.Options(DEFAULT_MAX_NUMBER_OF_ROLES);
    this.resource.logAdapter = new LogAdapter();
    this.resource.runtimeEnvironment = Mockito.mock(RuntimeEnvironment.class);
    this.resource.mpaCatalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (this.resource.mpaCatalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));

    this.resource.projectRoleActivator = Mockito.mock(ProjectRoleActivator.class);
    this.resource.justificationPolicy = Mockito.mock(JustificationPolicy.class);
    this.resource.tokenSigner = Mockito.mock(TokenSigner.class);

    this.notificationService = Mockito.mock(NotificationService.class);
    when(this.notificationService.canSendNotifications()).thenReturn(true);

    this.resource.notificationServices = Mockito.mock(Instance.class);
    when(this.resource.notificationServices.stream()).thenReturn(List.of(notificationService).stream());
    when(this.resource.notificationServices.iterator()).thenReturn(List.of(notificationService).iterator());

    when(this.resource.runtimeEnvironment.createAbsoluteUriBuilder(any(UriInfo.class)))
      .thenReturn(UriBuilder.fromUri("https://localhost/"));
  }

//TODO: remove
}