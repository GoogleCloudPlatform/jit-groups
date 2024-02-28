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

import com.google.solutions.jitaccess.catalog.auth.*;
import org.mockito.Mockito;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;

public class Subjects {

  public static Subject createWithPrincipals(
    UserId user,
    Set<Principal> otherPrincipals
  ) {
    var defaultPrincipals = Stream.of(
      new Principal(user),
      new Principal(UserClassId.AUTHENTICATED_USERS));

    var subject = Mockito.mock(Subject.class);
    when(subject.user()).thenReturn(user);
    when(subject.principals()).thenReturn(
      Stream
        .concat(otherPrincipals.stream(), defaultPrincipals)
        .collect(Collectors.toSet()));

    return subject;
  }

  public static Subject createWithPrincipalIds(
    UserId user,
    Set<PrincipalId> otherPrincipals
  ) {
    return createWithPrincipals(
      user,
      otherPrincipals
        .stream()
        .map(p -> new Principal(p))
        .collect(Collectors.toSet()));
  }

  public static Subject create(UserId user) {
    return createWithPrincipals(user, Set.<Principal>of());
  }
}
