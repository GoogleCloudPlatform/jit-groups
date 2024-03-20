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
import com.google.solutions.jitaccess.core.catalog.JustificationPolicy;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.web.LogAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class TestMetadataAction {
  private static final String DEFAULT_HINT = "hint";
  private static final int DEFAULT_MIN_NUMBER_OF_REVIEWERS = 1;
  private static final int DEFAULT_MAX_NUMBER_OF_REVIEWERS = 10;
  private static final Duration DEFAULT_ACTIVATION_DURATION = Duration.ofMinutes(5);

  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");


  @Test
  public void responseContainsJustificationHintAndUser() throws Exception {
    var justificationPolicy = Mockito.mock(JustificationPolicy.class);
    when(justificationPolicy.hint())
      .thenReturn(DEFAULT_HINT);

    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when(catalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var action = new MetadataAction(
      new LogAdapter(),
      catalog,
      justificationPolicy);

    var response = action.execute(Mocks.createIapPrincipalMock(SAMPLE_USER));
    assertEquals(DEFAULT_HINT, response.justificationHint);
    assertEquals(SAMPLE_USER.email, response.signedInUser.email);
  }
}
