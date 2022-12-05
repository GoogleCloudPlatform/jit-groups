//
// Copyright 2022 Google LLC
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

package com.google.solutions.jitaccess.core.services;

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.adapters.IamCredentialsAdapter;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserId;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ReviewTokenService {
  private final IamCredentialsAdapter iamCredentialsAdapter;
  private final Options options;
  private final TokenVerifier tokenVerifier;

  public ReviewTokenService(
    IamCredentialsAdapter iamCredentialsAdapter,
    Options options
  ) {
    Preconditions.checkNotNull(iamCredentialsAdapter, "iamCredentialsAdapter");
    Preconditions.checkNotNull(options, "options");

    this.options = options;
    this.iamCredentialsAdapter = iamCredentialsAdapter;

    //
    // Create verifier to check signature and obligatory claims.
    //
    this.tokenVerifier = TokenVerifier
      .newBuilder()
      .setCertificatesLocation(IamCredentialsAdapter.getJwksUrl(options.serviceAccount))
      .setIssuer(options.serviceAccount.email)
      .setAudience(options.serviceAccount.email)
      .build();
  }

  public String createToken(Payload payload) throws AccessException, IOException {
    Preconditions.checkNotNull(payload, "payload");

    //
    // Add obligatory claims.
    //
    var now = Instant.now();
    var jwtPayload = payload.payload
      .clone()
      .setAudience(this.options.serviceAccount.email)
      .setIssuer(this.options.serviceAccount.email)
      .setIssuedAtTimeSeconds(now.getEpochSecond())
      .setExpirationTimeSeconds(now.plus(this.options.tokenValidity).getEpochSecond());

    return this.iamCredentialsAdapter.signJwt(
      this.options.serviceAccount,
      jwtPayload);
  }

  public Payload verifyToken(
    String token
  ) throws TokenVerifier.VerificationException {
    Preconditions.checkNotNull(token, "token");

    var decodedToken = this.tokenVerifier.verify(token);
    if (!decodedToken.getHeader().getAlgorithm().equals("RS256")) {
      //
      // Service account keys are RS256, anything else is fishy.
      //
      throw new TokenVerifier.VerificationException("The token uses the wrong algorithm");
    }

    return new Payload(decodedToken.getPayload());
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------


  /** Approval request data (used as token payload */
  public static class Payload {
    private final JsonWebToken.Payload payload;

    private Payload(JsonWebToken.Payload payload) {
      this.payload = payload;
    }

    public UserId getBeneficiary() {
      return new UserId(this.payload.get("beneficiary").toString());
    }

    public Collection<UserId> getReviewers() {
      var list = (List<String>)this.payload.get("reviewers");
      return list
        .stream()
        .map(email -> new UserId(email))
        .collect(Collectors.toList());
    }

    public String getJustification() {
      return this.payload.get("justification").toString();
    }

    public RoleBinding getRoleBinding() {
      return new RoleBinding(
        this.payload.get("resource").toString(),
        this.payload.get("role").toString());
    }

    public Instant getIssueTime() {
      return Instant.ofEpochSecond((Long)this.payload.get("iat"));
    }

    public Instant getExpiryTime() {
      return Instant.ofEpochSecond((Long)this.payload.get("exp"));
    }

    public String getIssuer() {
      return this.payload.getIssuer();
    }

    public String getAudience() {
      return (String)this.payload.getAudience();
    }

    public static class Builder {
      private final JsonWebToken.Payload payload = new JsonWebToken.Payload();

      public Builder setBeneficiary(UserId beneficiary) {
        Preconditions.checkNotNull(beneficiary);
        this.payload.set("beneficiary", beneficiary.email);
        return this;
      }

      public Builder setReviewers(Collection<UserId> reviewers) {
        Preconditions.checkNotNull(reviewers);
        this.payload.set(
          "reviewers",
          reviewers.stream().map(id -> id.email).collect(Collectors.toList()));
        return this;
      }

      public Builder setJustification(String justification) {
        Preconditions.checkNotNull(justification);
        this.payload.set("justification", justification);
        return this;
      }

      public Builder setRoleBinding(RoleBinding roleBinding) {
        Preconditions.checkNotNull(roleBinding);
        this.payload.set("resource", roleBinding.fullResourceName);
        this.payload.set("role", roleBinding.role);
        return this;
      }

      public Payload build() {
        return new Payload(this.payload);
      }
    }
  }

  public static class Options {
    public final UserId serviceAccount;
    public final Duration tokenValidity;

    public Options(UserId serviceAccount, Duration tokenValidity) {
      this.serviceAccount = serviceAccount;
      this.tokenValidity = tokenValidity;
    }
  }
}
