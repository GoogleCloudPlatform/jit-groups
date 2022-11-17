package com.google.solutions.jitaccess.core.services;

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.adapters.IamCredentialsAdapter;
import com.google.solutions.jitaccess.core.adapters.IntegrationTestEnvironment;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class TestTokenService {

  // -------------------------------------------------------------------------
  // createToken.
  // -------------------------------------------------------------------------

  @Test
  public void whenPayloadEmpty_ThenCreateTokenAddsObligatoryClaims() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;
    var tokenService = new TokenService(
      credentialsAdapter,
      new TokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var token = tokenService.createToken(new JsonWebToken.Payload());
    var verifiedPayload = tokenService.verifyToken(token);

    assertEquals(serviceAccount.getEmail(), verifiedPayload.getIssuer());
    assertEquals(serviceAccount.getEmail(), verifiedPayload.getAudience());
    assertNotNull(verifiedPayload.getExpirationTimeSeconds());
  }

  // -------------------------------------------------------------------------
  // verifyToken.
  // -------------------------------------------------------------------------

  @Test
  public void whenJwtMissesAudienceClaim_ThenVerifyTokenThrowsException() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;
    var tokenService = new TokenService(
      credentialsAdapter,
      new TokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload()
      .setIssuer(serviceAccount.getEmail());

    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    assertThrows(TokenVerifier.VerificationException.class,
      () -> tokenService.verifyToken(jwt));
  }

  @Test
  public void whenJwtMissesIssuerClaim_ThenVerifyThrowsException() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;
    var tokenService = new TokenService(
      credentialsAdapter,
      new TokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.getEmail());

    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    assertThrows(TokenVerifier.VerificationException.class,
      () -> tokenService.verifyToken(jwt));
  }

  @Test
  public void whenJwtSignedByWrongServiceAccount_ThenVerifyThrowsException() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.TEMPORARY_ACCESS_USER;
    var tokenService = new TokenService(
      credentialsAdapter,
      new TokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.getEmail())
      .setIssuer(serviceAccount.getEmail());

    var jwt = credentialsAdapter.signJwt(IntegrationTestEnvironment.NO_ACCESS_USER, payload);

    assertThrows(TokenVerifier.VerificationException.class,
      () -> tokenService.verifyToken(jwt));
  }
}
