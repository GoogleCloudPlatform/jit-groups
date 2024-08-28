//
// Copyright 2021 Google LLC
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

package com.google.solutions.jitaccess.apis.clients;

import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ITestResourceManagerClient {
  private static final String REQUEST_REASON = "testing";

  //---------------------------------------------------------------------
  // modifyIamPolicy.
  //---------------------------------------------------------------------

  @Test
  public void modifyIamPolicy_project() throws Exception {
    var adapter = new ResourceManagerClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    adapter.modifyIamPolicy(
      ITestEnvironment.PROJECT_ID,
      policy -> {},
      REQUEST_REASON);
  }
}