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

package com.google.solutions.jitaccess.auth;

import com.google.solutions.jitaccess.apis.Domain;
import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestCachedSubjectResolver {
  private static final Domain SAMPLE_DOMAIN = new Domain("example.com", Domain.Type.PRIMARY);
  private static final EndUserId SAMPLE_USER = new EndUserId("user@example.com");
  private static final Executor EXECUTOR = command -> command.run();

  //---------------------------------------------------------------------------
  // resolve
  //---------------------------------------------------------------------------

  @Test
  public void resolve() throws Exception {
    var mapping = new GroupMapping(SAMPLE_DOMAIN);

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.listMembershipsByUser(eq(SAMPLE_USER)))
      .thenReturn(List.of());

    var resolver = new CachedSubjectResolver(
      groupsClient,
      mapping,
      EXECUTOR,
      Mockito.mock(Logger.class),
      new CachedSubjectResolver.Options(
        Duration.ofMinutes(1),
        new Directory(SAMPLE_DOMAIN)));

    resolver.resolveGroupPrincipals(SAMPLE_USER); // Triggers load
    resolver.resolveGroupPrincipals(SAMPLE_USER); // Triggers cache

    verify(groupsClient, times(1)).listMembershipsByUser(eq(SAMPLE_USER));
  }
}
