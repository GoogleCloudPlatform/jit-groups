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

package com.google.solutions.jitaccess.web;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.solutions.jitaccess.catalog.auth.EndUserId;
import com.google.solutions.jitaccess.catalog.auth.ServiceAccountId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.auth.Directory;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JWT assertion issued by IAP.
 */
class IapAssertion {
  private final @NotNull JsonWebToken.Payload payload;
  private final @NotNull UserId userId;

  IapAssertion(@NotNull JsonWebToken.Payload payload) {
    this.payload = payload;

    var email = this.payload.get("email").toString().toLowerCase();
    if (email.endsWith(".gserviceaccount.com")) {
      //
      // This is some kind of service account, but it could also be
      // a service agent.
      //
      this.userId = ServiceAccountId.parse(ServiceAccountId.TYPE + ":" + email)
        .orElseThrow(() -> new IllegalArgumentException(
          "The service account type is not supported"));
    }
    else {
      this.userId = new EndUserId(email);
    }
  }

  IapAssertion(@NotNull JsonWebSignature payload) {
    this(payload.getPayload());
  }

  /**
   * Get ID of the authenticated user.
   */
  public @NotNull UserId user() {
    return this.userId;
  }

  /**
   * Get directory that the user originates from.
   */
  public @NotNull Directory directory() {
    if (this.userId instanceof ServiceAccountId) {
      //
      // Service accounts are owned by a project. We can't be sure
      // if that's a standalone project or a project that belongs
      // to a Google Cloud organization.
      //
      // In either case, the 'hd' claim will be empty.
      //
      return Directory.PROJECT;
    }
    else {
      //
      // If the assertion contains a 'hd' claim, then it's a 'managed user',
      // i.e., a user that's managed by Cloud Identity or Workspace.
      //
      // If the 'hd' claim is missing, it must be a consumer account.
      //
      return Optional
        .ofNullable((String)this.payload.get("hd"))
        .map(hd -> new Directory(hd.toLowerCase()))
        .orElse(Directory.CONSUMER);
    }
  }

  /**
   * Extract device information (if available)
   */
  public @NotNull IapDevice device() {
    String deviceId = "unknown";
    List<String> accessLevels = List.of();

    if (this.payload.containsKey("google")) {
      var googleClaim = (Map<?, ?>) this.payload.get("google");

      if (googleClaim.containsKey("device_id")) {
        deviceId = googleClaim.get("device_id").toString();
      }

      if (googleClaim.containsKey("access_levels")) {
        accessLevels = ((Collection<?>) googleClaim.get("access_levels"))
          .stream()
          .map(Object::toString)
          .collect(Collectors.toList());
      }
    }

    return new IapDevice(deviceId, accessLevels);
  }
}
