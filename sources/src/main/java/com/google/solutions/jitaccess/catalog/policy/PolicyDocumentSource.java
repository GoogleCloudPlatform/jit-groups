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

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;

/**
 * Raw, unparsed source of a policy document.
 */
public abstract class PolicyDocumentSource {
  /**
   * Get YAML-formatted source.
   */
  public abstract @NotNull String yaml();

  /**
   * Get metadata about the origin of the policy.
   */
  public abstract @NotNull Policy.Metadata metadata();

  /**
   * Parse and validate source.
   */
  public abstract PolicyDocument parse() throws PolicyDocument.SyntaxException;

  /**
   * Create a policy document from an in-memory string.
   */
  public static @NotNull PolicyDocumentSource fromString(
    @NotNull String yaml,
    @NotNull Policy.Metadata metadata
  ) {
    return new PolicyDocumentSource() {
      @Override
      public @NotNull String yaml() {
        return yaml;
      }

      @Override
      public @NotNull Policy.Metadata metadata() {
        return metadata;
      }

      @Override
      public PolicyDocument parse() throws PolicyDocument.SyntaxException {
        return PolicyDocument.parse(this);
      }
    };
  }

  /**
   * Create a policy document from an in-memory string
   * using default metadata.
   */
  public static @NotNull PolicyDocumentSource fromString(
    @NotNull String yaml
  ) {
    return fromString(
      yaml,
      new Policy.Metadata(
        "memory",
        Instant.now()));
  }

  /**
   * Read policy document from a local file.
   */
  public static @NotNull PolicyDocumentSource fromFile(
    @NotNull File file
  ) throws IOException {
    if (!file.exists()) {
      throw new FileNotFoundException(
        String.format("The file '%s' does not exist", file.getAbsolutePath()));
    }

    return fromString(
      Files.readString(file.toPath()),
      new Policy.Metadata(
        file.getName(),
        Instant.ofEpochMilli(file.lastModified())));
  }

  /**
   * Reconstruct source for a policy.
   */
  public static @NotNull PolicyDocumentSource fromPolicy(
    @NotNull EnvironmentPolicy policy
  ) {
    var document = new PolicyDocument(policy);

    return new PolicyDocumentSource() {
      @Override
      public @NotNull String yaml() {
        return document.toString();
      }

      @Override
      public @NotNull Policy.Metadata metadata() {
        return document.policy().metadata();
      }

      @Override
      public PolicyDocument parse() {
        return document;
      }
    };
  }

  @Override
  public String toString() {
    return this.yaml();
  }
}
