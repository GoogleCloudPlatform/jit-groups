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

import com.google.solutions.jitaccess.catalog.policy.Property;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

class Inputs {
  private Inputs() {}

  /**
   * Copy input values.
   *
   * @throws IllegalArgumentException if the input values are incomplete.
   */
  public static void copyValues(
    @NotNull MultivaluedMap<String, String> source,
    @NotNull Collection<Property> target
  ) {
    for (var input : target) {
      if (source.containsKey(input.name())) {
        input.set(source.get(input.name()).get(0));
      }
      else if (input.isRequired()) {
        throw new IllegalArgumentException(
          String.format("'%s' is a required field", input.displayName()));
      }
    }
  }
}
