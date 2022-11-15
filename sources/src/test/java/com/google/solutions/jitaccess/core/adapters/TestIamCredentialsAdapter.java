package com.google.solutions.jitaccess.core.adapters;

import com.google.auth.oauth2.TokenVerifier;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestIamCredentialsAdapter {

  // -------------------------------------------------------------------------
  // signJwt.
  // -------------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenSignJwtThrowsException() throws Exception {
    var adapter = new IamCredentialsAdapter(IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS);

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.signJwt(IntegrationTestEnvironment.NO_ACCESS_USER, "{}"));
  }

  @Test
  public void whenCallerHasPermission_ThenSignJwtSucceeds() throws Exception {
    var adapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    var jwt = adapter.signJwt(IntegrationTestEnvironment.NO_ACCESS_USER, "{}");
    assertNotNull(jwt);
  }

  // -------------------------------------------------------------------------
  // getJwksUrl.
  // -------------------------------------------------------------------------

  @Test
  public void getJwksUrl() throws Exception {
    assertEquals(
      String.format(
        "https://www.googleapis.com/service_accounts/v1/metadata/jwk/%s",
        IntegrationTestEnvironment.NO_ACCESS_USER.getEmail()),
      IamCredentialsAdapter.getJwksUrl(IntegrationTestEnvironment.NO_ACCESS_USER));
  }

  // -------------------------------------------------------------------------
  // createJwtVerifier.
  // -------------------------------------------------------------------------

  @Test
  public void whenJwtMissesAudienceClaim_ThenVerifyThrowsException() throws Exception {
    var adapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;

    var jwt = adapter.signJwt(
      serviceAccount,
      String.format("{\"iss\":\"%s\"}", serviceAccount.getEmail()));

    assertThrows(TokenVerifier.VerificationException.class,
      () -> IamCredentialsAdapter
      .createJwtVerifier(IntegrationTestEnvironment.NO_ACCESS_USER)
      .verify(jwt));
  }

  @Test
  public void whenJwtMissesIssuerClaim_ThenVerifyThrowsException() throws Exception {
    var adapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;

    var jwt = adapter.signJwt(
      serviceAccount,
      String.format("{\"aud\":\"%s\"}", serviceAccount.getEmail()));

    assertThrows(TokenVerifier.VerificationException.class,
      () -> IamCredentialsAdapter
        .createJwtVerifier(IntegrationTestEnvironment.NO_ACCESS_USER)
        .verify(jwt));
  }

  @Test
  public void whenJwtSignedByWrongServiceAccount_ThenVerifyThrowsException() throws Exception {
    var adapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;

    var jwt = adapter.signJwt(
      serviceAccount,
      String.format(
        "{\"aud\":\"%s\", \"iss\":\"%s\"}",
        serviceAccount.getEmail(),
        serviceAccount.getEmail()));

    assertThrows(TokenVerifier.VerificationException.class,
      () -> IamCredentialsAdapter
        .createJwtVerifier(IntegrationTestEnvironment.TEMPORARY_ACCESS_USER)
        .verify(jwt));
  }

  @Test
  public void whenJwtValid_ThenVerifySucceeds() throws Exception {
    var adapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;

    var jwt = adapter.signJwt(
      serviceAccount,
      String.format(
        "{\"aud\":\"%s\", \"iss\":\"%s\"}",
        serviceAccount.getEmail(),
        serviceAccount.getEmail()));

    var verifiedJwt = IamCredentialsAdapter
        .createJwtVerifier(IntegrationTestEnvironment.NO_ACCESS_USER)
        .verify(jwt);

    assertEquals(serviceAccount.getEmail(), verifiedJwt.getPayload().getAudience());
    assertEquals(serviceAccount.getEmail(), verifiedJwt.getPayload().getIssuer());
  }
}
