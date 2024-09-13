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

package com.google.solutions.jitaccess.catalog;

import com.google.solutions.jitaccess.auth.GroupId;
import com.google.solutions.jitaccess.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocumentSource;
import com.google.solutions.jitaccess.catalog.provisioning.Environment;
import com.google.solutions.jitaccess.catalog.provisioning.Provisioner;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class Environments {
  public static Collection<Environment> create(
    @NotNull List<EnvironmentPolicy> policies
  ) {
    return policies.stream()
      .map(p -> {
        var provisioner = Mockito.mock(Provisioner.class);
        when(provisioner.cloudIdentityGroupId(any()))
          .thenAnswer(a -> new GroupId(((JitGroupId)a.getArgument(0)) + "@example.com"));

        return (Environment)new Environment(p.name(), p.description(), provisioner, Duration.ofDays(1)) {
          @Override
          public PolicyDocumentSource loadPolicy() {
            return PolicyDocumentSource.fromPolicy(p);
          }
        };
      })
      .toList();
  }

  public static Collection<Environment> create(EnvironmentPolicy policy) {
    return create(List.of(policy));
  }
}
