package com.google.solutions.jitaccess.core.adapters;

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestIamCredentialsAdapter {

  // -------------------------------------------------------------------------
  // signJwt.
  // -------------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenSignJwtThrowsException() {
    var adapter = new IamCredentialsAdapter(IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS);

    var payload = new JsonWebToken.Payload()
      .setAudience("test");

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.signJwt(IntegrationTestEnvironment.NO_ACCESS_USER, payload));
  }

  @Test
  public void whenCallerHasPermission_ThenSignJwtSucceeds() throws Exception {
    var adapter = new IamCredentialsAdapter(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
    var serviceAccount = IntegrationTestEnvironment.NO_ACCESS_USER;

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.email)
      .setIssuer(serviceAccount.email);

    var jwt = adapter.signJwt(serviceAccount, payload);
    assertNotNull(jwt);

    TokenVerifier
      .newBuilder()
      .setCertificatesLocation(IamCredentialsAdapter.getJwksUrl(serviceAccount))
      .setIssuer(serviceAccount.email)
      .setAudience(serviceAccount.email)
      .build()
      .verify(jwt);
  }

  // -------------------------------------------------------------------------
  // getJwksUrl.
  // -------------------------------------------------------------------------

  @Test
  public void getJwksUrl() {
    assertEquals(
      String.format(
        "https://www.googleapis.com/service_accounts/v1/metadata/jwk/%s",
        IntegrationTestEnvironment.NO_ACCESS_USER.email),
      IamCredentialsAdapter.getJwksUrl(IntegrationTestEnvironment.NO_ACCESS_USER));
  }
}
