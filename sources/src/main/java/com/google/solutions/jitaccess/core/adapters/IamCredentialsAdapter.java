package com.google.solutions.jitaccess.core.adapters;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.services.iamcredentials.v1.IAMCredentials;
import com.google.api.services.iamcredentials.v1.model.SignJwtRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.TokenVerifier;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.ApplicationVersion;
import com.google.solutions.jitaccess.core.NotAuthenticatedException;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.security.GeneralSecurityException;

/** Adapter for IAM Credentials API */
@ApplicationScoped
public class IamCredentialsAdapter {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private final GoogleCredentials credentials;

  private IAMCredentials createClient() throws IOException
  {
    try {
      return new IAMCredentials
        .Builder(
          HttpTransport.newTransport(),
          new GsonFactory(),
          new HttpCredentialsAdapter(this.credentials))
        .setApplicationName(ApplicationVersion.USER_AGENT)
        .build();
    }
    catch (GeneralSecurityException e) {
      throw new IOException("Creating a IAMCredentials client failed", e);
    }
  }

  public IamCredentialsAdapter(GoogleCredentials credentials)  {
    Preconditions.checkNotNull(credentials, "credentials");

    this.credentials = credentials;
  }

  /**
   * Sign a JWT using the Google-managed service account key.
   */
  public String signJwt(UserId serviceAccount, JsonWebToken.Payload payload)
    throws AccessException, IOException {
    Preconditions.checkNotNull(serviceAccount, "serviceAccount");

    try
    {
      if (payload.getFactory() == null) {
        payload.setFactory(new GsonFactory());
      }

      var payloadJson = payload.toString();
      assert (payloadJson.startsWith("{"));

      var request = new SignJwtRequest()
        .setPayload(payloadJson);

      return createClient()
        .projects()
        .serviceAccounts()
        .signJwt(
          String.format("projects/-/serviceAccounts/%s", serviceAccount.getEmail()),
          request)
        .execute()
        .getSignedJwt();
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(
            String.format("Denied access to service account '%s': %s", serviceAccount.getEmail(), e.getMessage()), e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }

  /**
   * Get JWKS location for service account key set.
   */
  public static String getJwksUrl(UserId serviceAccount) {
    return String.format("https://www.googleapis.com/service_accounts/v1/metadata/jwk/%s", serviceAccount.getEmail());
  }

  /**
   * Create verifier for JWT that have been signed using
   * the Google-managed service account key.
   */
  public static TokenVerifier createJwtVerifier(UserId serviceAccount)
  {
    return TokenVerifier
      .newBuilder() // TODO: check exp
      .setCertificatesLocation(getJwksUrl(serviceAccount))
      .setIssuer(serviceAccount.getEmail())
      .setAudience(serviceAccount.getEmail())
      .build();
  }
}
