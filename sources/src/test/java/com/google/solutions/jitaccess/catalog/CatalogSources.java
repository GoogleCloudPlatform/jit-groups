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

import com.google.solutions.jitaccess.catalog.auth.GroupId;
import com.google.solutions.jitaccess.catalog.auth.JitGroupId;
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.PolicyHeader;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class CatalogSources {

  public static Catalog.Source create(List<EnvironmentPolicy> policies) {
    return new Catalog.Source() {
      @Override
      public @NotNull Collection<PolicyHeader> environmentPolicies() {
        return policies.stream().map(p -> (PolicyHeader)p).toList();
      }

      @Override
      public @NotNull Optional<EnvironmentPolicy> environmentPolicy(@NotNull String name) {
        return policies
          .stream()
          .filter(p -> p.name().equals(name))
          .findFirst();
      }

      @Override
      public @NotNull Optional<Provisioner> provisioner(
        @NotNull Catalog catalog,
        @NotNull String name
      ) {
        var provisioner = Mockito.mock(Provisioner.class);
        when(provisioner.cloudIdentityGroupId(any()))
          .thenAnswer(a -> new GroupId(((JitGroupId)a.getArgument(0)) + "@example.com"));

        return Optional.of(provisioner);
      }
    };
  }

  public static Catalog.Source create(EnvironmentPolicy policy) {
    return create(List.of(policy));
  }
}
