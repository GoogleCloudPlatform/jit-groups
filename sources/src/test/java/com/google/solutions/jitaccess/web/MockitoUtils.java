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

import jakarta.enterprise.inject.Instance;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.Mockito.when;

public class MockitoUtils {
  /**
   * Create an Instance for a given object.
   */
  public static <T> Instance<T> toCdiInstance(T obj) {
    var instance = Mockito.mock(Instance.class);
    when(instance.stream()).thenReturn(List.of(obj).stream());
    when(instance.iterator()).thenReturn(List.of(obj).iterator());

    return instance;
  }
}
