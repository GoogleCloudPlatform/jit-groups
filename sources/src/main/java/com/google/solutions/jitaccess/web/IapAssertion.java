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
import com.google.solutions.jitaccess.catalog.auth.PrincipalId;
import com.google.solutions.jitaccess.catalog.auth.ServiceAccountId;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.auth.UserType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class IapAssertion {
  private final @NotNull JsonWebToken.Payload payload;
  private final @NotNull UserId userId;

  public IapAssertion(@NotNull JsonWebToken.Payload payload) {
    this.payload = payload;

    //
    // Extract email address, which could identify a service account
    // or a user account.
    //
    var email = this.payload.get("email").toString().toLowerCase();
    this.userId = Optional.<UserId>empty()
      .or(() -> ServiceAccountId.parse("serviceAccount:" + email)) // TODO: service agents
      .orElse(new EndUserId(email));
  }

  public IapAssertion(@NotNull JsonWebSignature payload) {
    this(payload.getPayload());
  }

  /**
   * Get email of the authenticated user.
   */
  public @NotNull EndUserId email() {
    return (EndUserId) this.userId; // TODO: Change type to allow service account -- UserPrincipalId?
  }


  /**
   * Get email the hosted domain of the authenticated user, if present.
   */
  public @NotNull Optional<String> hostedDomain() {
    return Optional
      .ofNullable((String)this.payload.get("hd"))
      .map(String::toLowerCase);
  }

  /**
   * Get the user account type of the current user.
   */
  public UserType userType() {
    if (this.userId instanceof ServiceAccountId) {
      //
      // NB. JWT assertions for service accounts never contain a 'hd' claim,
      // even if they belong to an organization-owned project.
      //
      return UserType.SERVICE_ACCOUNT;
    }
    else {
      //
      // For users, the presence of the 'hd' claim is a reliable indicator
      // of a managed user account.
      //
      return hostedDomain().isPresent()
        ? UserType.MANAGED
        : UserType.CONSUMER;
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
