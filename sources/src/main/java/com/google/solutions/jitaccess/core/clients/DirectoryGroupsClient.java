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
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClient;
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Key;
import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.admin.directory.model.Groups;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;

/**
 * Client for the Directory API.
 */
@ApplicationScoped
public class DirectoryGroupsClient {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/admin.directory.group.readonly";
  private final GoogleCredentials credentials;
  private final HttpTransport.Options httpOptions;

  public DirectoryGroupsClient(
    GoogleCredentials credentials,
    HttpTransport.Options httpOptions
  ) {
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.credentials = credentials;
    this.httpOptions = httpOptions;
  }

  private Directory2 createClient() throws IOException {
    try {
      return (Directory2)new Directory2.Builder(
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
  public Collection<Group> listDirectGroupMemberships(
    UserId user
  ) throws AccessException, IOException {
    try {
      return new ListGroups(createClient())
        .setUserKey(user.email)
        .execute()
        .getGroups();
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException("Access to Directory API was denied", e);
        case 400:
          throw new ResourceNotFoundException(
            String.format("The user '%s' does not exist", user), e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }

  //---------------------------------------------------------------------------
  // Surrogate class for Directory that works with google-api-client 2.0..
  //---------------------------------------------------------------------------

  private static class Directory2 extends AbstractGoogleJsonClient {
    public Directory2(Builder builder) {
      super(builder);
    }

    public static final class Builder extends AbstractGoogleJsonClient.Builder {
      public Builder(
        com.google.api.client.http.HttpTransport transport,
        JsonFactory jsonFactory,
        HttpRequestInitializer requestInitializer
      ) {
        super(
          transport,
          jsonFactory,
          "https://www.googleapis.com/",
          "admin/directory/v1/",
          requestInitializer,
          false);
        this.setBatchPath("batch/admin/directory_v1");
      }

      public Directory2 build() {
        return new Directory2(this);
      }
    }
  }

  private static class ListGroups extends AbstractGoogleJsonClientRequest<Groups> {
    @Key
    private String userKey;

    public ListGroups(AbstractGoogleJsonClient abstractGoogleJsonClient) {
      super(
        abstractGoogleJsonClient,
        "GET",
        "groups",
        (Object)null,
        Groups.class);
    }

    public String getUserKey() {
      return this.userKey;
    }

    public ListGroups setUserKey(String userKey) {
      this.userKey = userKey;
      return this;
    }
  }
}
