package com.google.solutions.jitaccess.core.services;

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.google.solutions.jitaccess.core.adapters.IamCredentialsAdapter;
import com.google.solutions.jitaccess.core.adapters.IntegrationTestEnvironment;
import com.google.solutions.jitaccess.core.data.UserId;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class TestTokenService {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");

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

    var payload = new JsonWebToken.Payload()
      .setSubject(SAMPLE_USER.email);

    var token = tokenService.createToken(payload);
    var verifiedPayload = tokenService.verifyToken(token, SAMPLE_USER);

    assertEquals(serviceAccount.email, verifiedPayload.getIssuer());
    assertEquals(serviceAccount.email, verifiedPayload.getAudience());
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
      .setIssuer(serviceAccount.email)
      .setSubject(SAMPLE_USER.email);

    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    assertThrows(TokenVerifier.VerificationException.class,
      () -> tokenService.verifyToken(jwt, SAMPLE_USER));
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
      .setAudience(serviceAccount.email)
      .setSubject(SAMPLE_USER.email);

    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    assertThrows(TokenVerifier.VerificationException.class,
      () -> tokenService.verifyToken(jwt, SAMPLE_USER));
  }

  @Test
  public void whenJwtMissesSubject_ThenVerifyThrowsException() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;
    var tokenService = new TokenService(
      credentialsAdapter,
      new TokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.email)
      .setIssuer(serviceAccount.email);

    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    assertThrows(TokenVerifier.VerificationException.class,
      () -> tokenService.verifyToken(jwt, SAMPLE_USER));
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
      .setAudience(serviceAccount.email)
      .setIssuer(serviceAccount.email)
      .setSubject(SAMPLE_USER.email);

    var jwt = credentialsAdapter.signJwt(IntegrationTestEnvironment.NO_ACCESS_USER, payload);

    assertThrows(TokenVerifier.VerificationException.class,
      () -> tokenService.verifyToken(jwt, SAMPLE_USER));
  }

  @Test
  public void whenJwtValid_ThenVerifySucceeds() throws Exception {
    var credentialsAdapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;
    var tokenService = new TokenService(
      credentialsAdapter,
      new TokenService.Options(
        serviceAccount,
        Duration.ofMinutes(5)));

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.email)
      .setIssuer(serviceAccount.email)
      .setSubject(SAMPLE_USER.email);

    var jwt = credentialsAdapter.signJwt(serviceAccount, payload);

    tokenService.verifyToken(jwt, SAMPLE_USER);
  }
}
