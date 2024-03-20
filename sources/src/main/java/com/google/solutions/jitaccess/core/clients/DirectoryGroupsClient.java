//
// Copyright 2023 Google LLC
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

package com.google.solutions.jitaccess.core.clients;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Member;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.UserId;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Client for the Directory API.
 */
@Singleton
public class DirectoryGroupsClient {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/admin.directory.group.readonly";

  private final @NotNull Options options;
  private final @NotNull GoogleCredentials credentials;
  private final @NotNull HttpTransport.Options httpOptions;

  public DirectoryGroupsClient(
    @NotNull GoogleCredentials credentials,
    @NotNull Options options,
    @NotNull HttpTransport.Options httpOptions
  ) {
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(options, "options");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.credentials = credentials;
    this.options = options;
    this.httpOptions = httpOptions;
  }

  private @NotNull Directory createClient() throws IOException {
    try {
      return new Directory.Builder(
          HttpTransport.newTransport(),
          new GsonFactory(),
          HttpTransport.newAuthenticatingRequestInitializer(this.credentials, this.httpOptions))
        .setApplicationName(ApplicationVersion.USER_AGENT)
        .build();
    }
    catch (GeneralSecurityException e) {
      throw new IOException("Creating a Directory client failed", e);
    }
  }

  /**
   * List all groups a given user is a direct member of.
   */
  public @NotNull Collection<Group> listDirectGroupMemberships(
    @NotNull UserId user
  ) throws AccessException, IOException {
    try {
      //
      // NB. Using userKey doesn't work for service account,
      // so we have to use a query.
      //
      var result = createClient()
        .groups()
        .list()
        .setCustomer(this.options.customerId)
        .setQuery(String.format("memberKey=%s", user.email))
        .execute();

      return result.getGroups() != null
        ? result.getGroups()
        : List.of();
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException("Access to Directory API was denied", e);
        case 400:
        case 404:
          throw new ResourceNotFoundException(
            String.format(
              "The customer ID '%s' is invalid, the user '%s' does not exist, or it belongs to an unknown domain",
              this.options.customerId,
              user),
            e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }

  /**
   * List users that are a direct member of the given group.
   */
  public @NotNull Collection<Member> listDirectGroupMembers(
    @NotNull String groupEmail
  ) throws AccessException, IOException {
    try {
      var result = createClient()
        .members()
        .list(groupEmail)
        .execute();

      if (result.getMembers() == null) {
        return List.of();
      }

      return result.getMembers()
        .stream()
        .filter(member -> "USER".equals(member.getType()))
        .filter(member -> "ACTIVE".equals(member.getStatus()))
        .collect(Collectors.toList());
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException("Access to Directory API was denied", e);
        case 404:
          throw new ResourceNotFoundException(
            String.format("The group '%s' does not exist", groupEmail), e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }

  //---------------------------------------------------------------------------
  // Inner classes.
  //---------------------------------------------------------------------------

  public record Options(
    @NotNull String customerId
  ) {
    public Options {
      Preconditions.checkNotNull(customerId, "customerId");
      Preconditions.checkArgument(
        customerId.startsWith("C"),
        "Customer ID must use format Cxxxxxxxx");
    }
  }
}
