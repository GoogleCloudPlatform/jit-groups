package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.JustificationPolicy;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.iap.DeviceInfo;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class TestMetadataResource {
  private static final String DEFAULT_HINT = "hint";
  private static final int DEFAULT_MIN_NUMBER_OF_REVIEWERS = 1;
  private static final int DEFAULT_MAX_NUMBER_OF_REVIEWERS = 10;
  private static final Duration DEFAULT_ACTIVATION_DURATION = Duration.ofMinutes(5);

  private static final UserEmail SAMPLE_USER = new UserEmail("user-1@example.com");


  @Test
  public void responseContainsJustificationHintAndUser() throws Exception {
    var justificationPolicy = Mockito.mock(JustificationPolicy.class);
    when(justificationPolicy.hint())
      .thenReturn(DEFAULT_HINT);

    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when(catalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var resource = new MetadataResource(
      new LogAdapter(),
      catalog,
      justificationPolicy);

    var response = resource.get(new MockIapPrincipal(SAMPLE_USER));
    assertEquals(DEFAULT_HINT, response.justificationHint);
    assertEquals(SAMPLE_USER.email, response.signedInUser.email);
  }
}
