package com.google.solutions.jitaccess.apis.clients;

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ITestIamCredentialsClient {

  // -------------------------------------------------------------------------
  // signJwt.
  // -------------------------------------------------------------------------

  @Test
  public void signJwt_whenUnauthenticated_thenThrowsException() {
    var adapter = new IamCredentialsClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    var payload = new JsonWebToken.Payload()
      .setAudience("test");

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.signJwt(ITestEnvironment.NO_ACCESS_USER, payload));
  }

  @Test
  public void signJwt() throws Exception {
    var adapter = new IamCredentialsClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    var serviceAccount = ITestEnvironment.NO_ACCESS_USER;

    var payload = new JsonWebToken.Payload()
      .setAudience(serviceAccount.email)
      .setIssuer(serviceAccount.email);

    var jwt = adapter.signJwt(serviceAccount, payload);
    assertNotNull(jwt);

    TokenVerifier
      .newBuilder()
      .setCertificatesLocation(IamCredentialsClient.getJwksUrl(serviceAccount))
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
        ITestEnvironment.NO_ACCESS_USER.email),
      IamCredentialsClient.getJwksUrl(ITestEnvironment.NO_ACCESS_USER));
  }
}
