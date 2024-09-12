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

package com.google.solutions.jitaccess.catalog.policy;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class TestPolicyDocumentSource {

  //---------------------------------------------------------------------------
  // toString.
  //---------------------------------------------------------------------------

  @Test
  public void toString_returnsYaml() {
    assertEquals(
      "yaml",
      new PolicyDocumentSource("yaml", new Policy.Metadata("test", Instant.now())).toString());
  }

  //---------------------------------------------------------------------------
  // fromFile.
  //---------------------------------------------------------------------------

  @Test
  public void fromFile_whenFileNotFound() {
    assertThrows(
      FileNotFoundException.class,
      () -> PolicyDocumentSource.fromFile(new File("doesnotexist.yaml")));
  }

  @Test
  public void fromFile() throws Exception {
    var yaml = "schemaVersion: 1\n" +
      "environment: \n" +
      "  name: 'env-1'";

    var tempFile = File.createTempFile("policy", "yaml");
    Files.writeString(tempFile.toPath(), yaml);

    var source = PolicyDocumentSource.fromFile(tempFile);
    assertEquals(yaml, source.yaml());
    assertEquals(tempFile.getName(), source.metadata().source());
    assertFalse(source.metadata().lastModified().isAfter(Instant.now()));
  }
}
