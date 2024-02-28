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

package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.ApplicationVersion;
import com.google.solutions.jitaccess.catalog.auth.GroupId;
import com.google.solutions.jitaccess.catalog.auth.Principal;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.web.Application;
import com.google.solutions.jitaccess.web.RequestContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestUserResource {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");
  private static final GroupId SAMPLE_GROUP = new GroupId("group-1@example.com");

  private static Subject createSubject() {
    var subject = Mockito.mock(Subject.class);
    when(subject.principals())
      .thenReturn(Set.of(new Principal(SAMPLE_USER), new Principal(SAMPLE_GROUP)));
    when(subject.user())
      .thenReturn(SAMPLE_USER);

    return subject;
  }

  //---------------------------------------------------------------------------
  // context.
  //---------------------------------------------------------------------------

  @Test
  public void context_whenDebugModeOff() {
    var resource = new UserResource();
    resource.requestContext = Mockito.mock(RequestContext.class);
    resource.application = Mockito.mock(Application.class);

    when(resource.application.isDebugModeEnabled())
      .thenReturn(false);

    when(resource.requestContext.user())
      .thenReturn(SAMPLE_USER);

    var contextInfo = resource.get();

    assertEquals(SAMPLE_USER.email, contextInfo.subject().email());
    assertNull(contextInfo.subject().principals());
    assertEquals(ApplicationVersion.VERSION_STRING, contextInfo.application().version());
    assertFalse(contextInfo.application().debugMode());

    verify(resource.requestContext, times(0)).subject();
  }

  @Test
  public void context_whenDebugModeOn() {
    var resource = new UserResource();
    resource.requestContext = Mockito.mock(RequestContext.class);
    resource.application = Mockito.mock(Application.class);

    when(resource.application.isDebugModeEnabled())
      .thenReturn(true);

    var subject = createSubject();
    when(resource.requestContext.subject())
      .thenReturn(subject);
    when(resource.requestContext.user())
      .thenReturn(SAMPLE_USER);

    var contextInfo = resource.get();

    assertEquals(SAMPLE_USER.email, contextInfo.subject().email());
    assertEquals(2, contextInfo.subject().principals().size());
    assertEquals(ApplicationVersion.VERSION_STRING, contextInfo.application().version());
    assertTrue(contextInfo.application().debugMode());
  }
}
