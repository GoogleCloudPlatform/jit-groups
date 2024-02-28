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

import com.google.solutions.jitaccess.catalog.Subjects;
import com.google.solutions.jitaccess.catalog.auth.GroupId;
import com.google.solutions.jitaccess.catalog.auth.SubjectResolver;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestRequestContext {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");
  private static final GroupId SAMPLE_GROUP = new GroupId("group-1@example.com");

  @Test
  public void whenNotAuthenticated() {
    var resolver = Mockito.mock(SubjectResolver.class);
    var context = new RequestContext(resolver);

    assertFalse(context.isAuthenticated());

    assertEquals("anonymous", context.subject().user().email);
    assertEquals(1, context.subject().principals().size());
    assertEquals(
      context.subject().user(),
      context.subject().principals().stream().findFirst().get().id());

    assertEquals(IapDevice.UNKNOWN.deviceId(), context.device().deviceId());
  }

  @Test
  public void whenAuthenticated() throws Exception {
    var subject = Subjects.createWithPrincipalIds(SAMPLE_USER, Set.of(SAMPLE_GROUP));

    var resolver = Mockito.mock(SubjectResolver.class);
    when(resolver.resolve(eq(SAMPLE_USER)))
      .thenReturn(subject);

    var context = new RequestContext(resolver);
    context.authenticate(SAMPLE_USER, new IapDevice("device-1", List.of()));

    assertEquals(SAMPLE_USER.email, context.subject().user().email);
    assertEquals(3, context.subject().principals().size());
    assertEquals(3, context.subject().principals().size()); // Invoke again to trigger cache

    assertEquals("device-1", context.device().deviceId());

    verify(resolver, times(1)).resolve(eq(SAMPLE_USER));
  }
}
