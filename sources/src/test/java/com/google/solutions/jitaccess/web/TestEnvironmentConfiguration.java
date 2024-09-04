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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.solutions.jitaccess.apis.ProjectId;
import com.google.solutions.jitaccess.apis.clients.HttpTransport;
import com.google.solutions.jitaccess.catalog.auth.ServiceAccountId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class TestEnvironmentConfiguration {

  //---------------------------------------------------------------------------
  // forFile.
  //---------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {
    "/not-a-url",
    "file: ",
    "file:."
  })
  public void forFile_whenFilePathInvalid(String path) {
    assertThrows(
      IllegalArgumentException.class,
      () -> EnvironmentConfiguration.forFile(
        path,
        Mockito.mock(GoogleCredentials.class)));
  }

  @Test
  public void forFile() throws Exception {
    var tempFile = File.createTempFile("policy", ".yaml");
    Files.writeString(tempFile.toPath(), "");

    var credentials = Mockito.mock(GoogleCredentials.class);
    var configuration = EnvironmentConfiguration.forFile(
      tempFile.toURI().toString(),
      credentials);

    assertEquals(tempFile.getName(), configuration.name() + ".yaml");
    assertSame(credentials, configuration.resourceCredentials());
  }

  @Test
  public void forFile_whenFileNotFound() throws Exception {
    assertThrows(
      FileNotFoundException.class,
      () -> EnvironmentConfiguration.forFile(
        "file:/does-not-exist.yaml",
        Mockito.mock(GoogleCredentials.class)));
  }

  //---------------------------------------------------------------------------
  // forFile.
  //---------------------------------------------------------------------------

  @Test
  public void forResource_whenResourcePathNotFound() {
    assertThrows(
      FileNotFoundException.class,
      () -> EnvironmentConfiguration.forResource(
        "name",
        "description",
        "does-not-exist.yaml",
        Mockito.mock(GoogleCredentials.class)));
  }

  //---------------------------------------------------------------------------
  // forServiceAccount.
  //---------------------------------------------------------------------------

  @Test
  public void forServiceAccount_whenServiceAccountIdLacksPrefix() {
    assertThrows(
      IllegalArgumentException.class,
      () -> EnvironmentConfiguration.forServiceAccount(
        new ServiceAccountId("no-prefix", new ProjectId("project-1")),
        new ServiceAccountId("app", new ProjectId("project-1")),
        Mockito.mock(GoogleCredentials.class),
        HttpTransport.Options.DEFAULT));
  }

  @Test
  public void forServiceAccount() {
    var configuration = EnvironmentConfiguration.forServiceAccount(
      new ServiceAccountId("jit-environment", new ProjectId("project-1")),
      new ServiceAccountId("app", new ProjectId("project-1")),
      Mockito.mock(GoogleCredentials.class),
      HttpTransport.Options.DEFAULT);

    assertEquals("environment", configuration.name());
    assertInstanceOf(ImpersonatedCredentials.class, configuration.resourceCredentials());
  }

  //---------------------------------------------------------------------------
  // inertExample.
  //---------------------------------------------------------------------------

  @Test
  public void inertExample() throws Exception {
    var policy = EnvironmentConfiguration
      .inertExample()
      .loadPolicy();
    assertNotEquals(0, policy.systems().size());
  }
}
