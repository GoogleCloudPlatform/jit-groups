package com.google.solutions.jitaccess.web.proposal;

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.apis.clients.AccessException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;

public interface TokenSigner {

  /**
   * Create a signed JWT for a given payload.
   */
  @NotNull ServiceAccountSigner.TokenWithExpiry sign(
    @NotNull JsonWebToken.Payload payload,
    @NotNull Instant expiry
  ) throws AccessException, IOException;

  /**
   * Decode and verify a JWT.
   */
  JsonWebToken.Payload verify(
    @NotNull String token
  ) throws TokenVerifier.VerificationException;

  record TokenWithExpiry(
    @NotNull String token,
    @NotNull Instant issueTime,
    @NotNull Instant expiryTime
  ) {
    public TokenWithExpiry {
      Preconditions.checkNotNull(token, "token");
      Preconditions.checkArgument(expiryTime.isAfter(issueTime));
      Preconditions.checkArgument(expiryTime.isAfter(Instant.now()));
    }
  }
}
