package com.google.solutions.jitaccess.core.services;

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.adapters.IamCredentialsAdapter;
import com.google.solutions.jitaccess.core.adapters.UserId;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class TokenService {
  private final IamCredentialsAdapter iamCredentialsAdapter;
  private final Options options;
  private final TokenVerifier tokenVerifier;

  public TokenService(
    IamCredentialsAdapter iamCredentialsAdapter,
    Options options) {

    Preconditions.checkNotNull(iamCredentialsAdapter, "iamCredentialsAdapter");
    Preconditions.checkNotNull(options, "options");

    this.options = options;
    this.iamCredentialsAdapter = iamCredentialsAdapter;

    //
    // Create verifier to check signature and obligatory claims.
    //
    this.tokenVerifier = TokenVerifier
      .newBuilder()
      .setCertificatesLocation(iamCredentialsAdapter.getJwksUrl(options.getServiceAccount()))
      .setIssuer(options.getServiceAccount().getEmail())
      .setAudience(options.getServiceAccount().getEmail())
      .build();
  }

  public String createToken(JsonWebToken.Payload payload) throws AccessException, IOException {
    Preconditions.checkNotNull(payload, "payload");

    //
    // Add obligatory claims.
    //
    payload = payload
      .clone()
      .setAudience(this.options.getServiceAccount().getEmail())
      .setIssuer(this.options.getServiceAccount().getEmail())
      .setExpirationTimeSeconds(Instant.now().plus(this.options.getTokenValidity()).getEpochSecond());

    return this.iamCredentialsAdapter.signJwt(
      this.options.getServiceAccount(),
      payload);
  }

  public JsonWebToken.Payload verifyToken(
    String token,
    UserId expectedSubject) throws TokenVerifier.VerificationException {

    Preconditions.checkNotNull(token, "token");
    Preconditions.checkNotNull(expectedSubject, "expectedSubject");

    var decodedToken = this.tokenVerifier.verify(token);
    if (!decodedToken.getHeader().getAlgorithm().equals("RS256")) {
      //
      // Service account keys are RS256, anything else is fishy.
      //
      throw new TokenVerifier.VerificationException("The token uses the wrong algorithm");
    }

    if (!expectedSubject.getEmail().equals(decodedToken.getPayload().getSubject())) {
      throw new TokenVerifier.VerificationException("The token was issued to a different subject");
    }

    return decodedToken.getPayload();
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  public static class Options {
    private final UserId serviceAccount;
    private final Duration tokenValidity;

    public Options(UserId serviceAccount, Duration tokenValidity) {
      this.serviceAccount = serviceAccount;
      this.tokenValidity = tokenValidity;
    }

    public UserId getServiceAccount() {
      return serviceAccount;
    }

    public Duration getTokenValidity() {
      return tokenValidity;
    }
  }
}
