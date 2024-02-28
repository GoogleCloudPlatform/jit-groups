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
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class TestInputs {
  @Test
  public void copyValues_whenTargetEmpty() {
    var source = new MultivaluedHashMap<String, String>();
    source.add("one", "1");

    Inputs.copyValues(source, List.of());
  }

  @Test
  public void copyValues_whenOptionalValueMissing() {
    var source = new MultivaluedHashMap<String, String>();
    source.add("one", "1");

    var property1 = Mockito.mock(Property.class);
    when(property1.name()).thenReturn("one");
    when(property1.isRequired()).thenReturn(false);

    var property2 = Mockito.mock(Property.class);
    when(property2.name()).thenReturn("two");
    when(property2.isRequired()).thenReturn(false);

    Inputs.copyValues(source, List.of(property1, property2));

    verify(property1, times(1)).set(eq("1"));
    verify(property2, never()).set(anyString());
  }

  @Test
  public void copyValues_whenRequiredValueMissing() {
    var source = new MultivaluedHashMap<String, String>();
    source.add("one", "1");

    var property1 = Mockito.mock(Property.class);
    when(property1.name()).thenReturn("one");
    when(property1.isRequired()).thenReturn(true);

    var property2 = Mockito.mock(Property.class);
    when(property2.name()).thenReturn("two");
    when(property2.isRequired()).thenReturn(true);

    assertThrows(
      IllegalArgumentException.class,
      () -> Inputs.copyValues(source, List.of(property1, property2)));
  }

  @Test
  public void copyValues() {
    var source = new MultivaluedHashMap<String, String>();
    source.add("one", "1");
    source.add("two", "2");

    var property1 = Mockito.mock(Property.class);
    when(property1.name()).thenReturn("one");
    when(property1.isRequired()).thenReturn(true);

    var property2 = Mockito.mock(Property.class);
    when(property2.name()).thenReturn("two");
    when(property2.isRequired()).thenReturn(true);

    Inputs.copyValues(source, List.of(property1, property2));

    verify(property1, times(1)).set(eq("1"));
    verify(property2, times(1)).set(eq("2"));
  }
}
