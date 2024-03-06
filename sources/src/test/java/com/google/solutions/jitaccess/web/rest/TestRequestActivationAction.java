package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.catalog.JustificationPolicy;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.TokenSigner;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleActivator;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

public class TestRequestActivationAction {
  private static final UserEmail SAMPLE_USER = new UserEmail("user-1@example.com");
  private static final UserEmail SAMPLE_USER_2 = new UserEmail("user-2@example.com");
  private static final Duration DEFAULT_ACTIVATION_DURATION = Duration.ofMinutes(5);
  private static final int DEFAULT_MIN_NUMBER_OF_REVIEWERS = 1;
  private static final int DEFAULT_MAX_NUMBER_OF_REVIEWERS = 10;
  private static final String SAMPLE_TOKEN = "eySAMPLE";
  private static final TokenSigner.TokenWithExpiry SAMPLE_TOKEN_WITH_EXPIRY =
    new TokenSigner.TokenWithExpiry(
      SAMPLE_TOKEN,
      Instant.now(),
      Instant.now().plusSeconds(10));

  @Test
  public void whenProjectIsNull_ThenActionThrowsException() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var action = new RequestActivationAction(
      new LogAdapter(),
      Mocks.createRuntimeEnvironmentMock(),
      Mockito.mock(ProjectRoleActivator.class),
      MockitoUtils.toCdiInstance(Mocks.createNotificationServiceMock(true)),
      catalog,
      Mockito.mock(TokenSigner.class));

    var request = new RequestActivationAction.RequestEntity();

    assertThrows(
      IllegalArgumentException.class,
      () -> action.execute(
        new MockIapPrincipal(SAMPLE_USER),
        "",
        request,
        Mocks.createUriInfoMock()));
  }

  @Test
  public void whenRoleEmpty_ThenActionThrowsException() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var action = new RequestActivationAction(
      new LogAdapter(),
      Mocks.createRuntimeEnvironmentMock(),
      Mockito.mock(ProjectRoleActivator.class),
      MockitoUtils.toCdiInstance(Mocks.createNotificationServiceMock(true)),
      catalog,
      Mockito.mock(TokenSigner.class));

    var request = new RequestActivationAction.RequestEntity();
    request.peers = List.of(SAMPLE_USER.email);
    request.role = null;

    assertThrows(
      IllegalArgumentException.class,
      () -> action.execute(
        new MockIapPrincipal(SAMPLE_USER),
        "project-1",
        request,
        Mocks.createUriInfoMock()));
  }

  @Test
  public void whenPeersEmpty_ThenActionThrowsException() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var action = new RequestActivationAction(
      new LogAdapter(),
      Mocks.createRuntimeEnvironmentMock(),
      Mockito.mock(ProjectRoleActivator.class),
      MockitoUtils.toCdiInstance(Mocks.createNotificationServiceMock(true)),
      catalog,
      Mockito.mock(TokenSigner.class));

    var request = new RequestActivationAction.RequestEntity();
    request.role = "roles/mock";
    request.peers = List.of();

    assertThrows(
      IllegalArgumentException.class,
      () -> action.execute(
        new MockIapPrincipal(SAMPLE_USER),
        "project-1",
        request,
        Mocks.createUriInfoMock()));
  }

  @Test
  public void whenTooFewPeersSelected_ThenActionThrowsException() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        2,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var action = new RequestActivationAction(
      new LogAdapter(),
      Mocks.createRuntimeEnvironmentMock(),
      Mockito.mock(ProjectRoleActivator.class),
      MockitoUtils.toCdiInstance(Mocks.createNotificationServiceMock(true)),
      catalog,
      Mockito.mock(TokenSigner.class));

    var request = new RequestActivationAction.RequestEntity();
    request.role = "roles/mock";
    request.peers = List.of("peer@example.com");

    assertThrows(
      IllegalArgumentException.class,
      () -> action.execute(
        new MockIapPrincipal(SAMPLE_USER),
        "project-1",
        request,
        Mocks.createUriInfoMock()));
  }

  @Test
  public void whenTooManyPeersSelected_ThenActionThrowsException() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var action = new RequestActivationAction(
      new LogAdapter(),
      Mocks.createRuntimeEnvironmentMock(),
      Mockito.mock(ProjectRoleActivator.class),
      MockitoUtils.toCdiInstance(Mocks.createNotificationServiceMock(true)),
      catalog,
      Mockito.mock(TokenSigner.class));

    var request = new RequestActivationAction.RequestEntity();
    request.role = "roles/mock";
    request.peers = Stream.generate(() -> "peer@example.com")
      .limit(DEFAULT_MAX_NUMBER_OF_REVIEWERS + 1)
      .collect(Collectors.toList());

    assertThrows(
      IllegalArgumentException.class,
      () -> action.execute(
        new MockIapPrincipal(SAMPLE_USER),
        "project-1",
        request,
        Mocks.createUriInfoMock()));
  }

  @Test
  public void whenJustificationEmpty_ThenActionThrowsException() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var action = new RequestActivationAction(
      new LogAdapter(),
      Mocks.createRuntimeEnvironmentMock(),
      Mockito.mock(ProjectRoleActivator.class),
      MockitoUtils.toCdiInstance(Mocks.createNotificationServiceMock(true)),
      catalog,
      Mockito.mock(TokenSigner.class));

    var request = new RequestActivationAction.RequestEntity();
    request.peers = List.of(SAMPLE_USER.email);
    request.role = "roles/mock";

    assertThrows(
      IllegalArgumentException.class,
      () -> action.execute(
        new MockIapPrincipal(SAMPLE_USER),
        "project-1",
        request,
        Mocks.createUriInfoMock()));
  }

  @Test
  public void whenNotificationsNotConfigured_ThenActionThrowsException() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var action = new RequestActivationAction(
      new LogAdapter(),
      Mocks.createRuntimeEnvironmentMock(),
      Mockito.mock(ProjectRoleActivator.class),
      MockitoUtils.toCdiInstance(Mocks.createNotificationServiceMock(false)),
      catalog,
      Mockito.mock(TokenSigner.class));

    var request = new RequestActivationAction.RequestEntity();
    request.role = "roles/mock";
    request.peers = List.of(SAMPLE_USER_2.email, SAMPLE_USER_2.email);
    request.justification = "justification";

    assertThrows(
      IllegalStateException.class,
      () -> action.execute(
        new MockIapPrincipal(SAMPLE_USER),
        "project-1",
        request,
        Mocks.createUriInfoMock()));
  }

  @Test
  public void whenActivatorThrowsException_ThenActionThrowsException() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var activator = Mockito.mock(ProjectRoleActivator.class);
    when(activator
      .createMpaRequest(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        any(),
        any(),
        any(),
        any(),
        any()))
      .thenThrow(new AccessDeniedException("mock"));

    var action = new RequestActivationAction(
      new LogAdapter(),
      Mocks.createRuntimeEnvironmentMock(),
      activator,
      MockitoUtils.toCdiInstance(Mocks.createNotificationServiceMock(true)),
      catalog,
      Mockito.mock(TokenSigner.class));

    var request = new RequestActivationAction.RequestEntity();
    request.role = "roles/mock";
    request.peers = List.of(SAMPLE_USER_2.email, SAMPLE_USER_2.email);
    request.justification = "justification";
    request.activationTimeout = 5;

    assertThrows(
      AccessDeniedException.class,
      () -> action.execute(
        new MockIapPrincipal(SAMPLE_USER),
        "project-1",
        request,
        Mocks.createUriInfoMock()));
  }

  @Test
  public void whenRequestValid_ThenActionSendsNotification() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var activator = new ProjectRoleActivator(
      catalog,
      Mockito.mock(ResourceManagerClient.class),
      Mockito.mock(JustificationPolicy.class));

    var tokenSigner =  Mockito.mock(TokenSigner.class);
    when(tokenSigner
      .sign(any(), any()))
      .thenReturn(SAMPLE_TOKEN_WITH_EXPIRY);

    var notificationService = Mocks.createNotificationServiceMock(true);

    var action = new RequestActivationAction(
      new LogAdapter(),
      Mocks.createRuntimeEnvironmentMock(),
      activator,
      MockitoUtils.toCdiInstance(notificationService),
      catalog,
      tokenSigner);

    var request = new RequestActivationAction.RequestEntity();
    request.role = "roles/mock";
    request.peers = List.of(SAMPLE_USER_2.email, SAMPLE_USER_2.email);
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = action.execute(
      new MockIapPrincipal(SAMPLE_USER),
      "project-1",
      request,
      Mocks.createUriInfoMock());

    verify(notificationService, times(1))
      .sendNotification(argThat(n -> n instanceof RequestActivationAction.RequestActivationNotification));
  }

  @Test
  public void whenRequestValid_ThenActionReturnsSuccessResponse() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var activator = new ProjectRoleActivator(
      catalog,
      Mockito.mock(ResourceManagerClient.class),
      Mockito.mock(JustificationPolicy.class));

    var tokenSigner =  Mockito.mock(TokenSigner.class);
    when(tokenSigner
      .sign(any(), any()))
      .thenReturn(SAMPLE_TOKEN_WITH_EXPIRY);

    var roleBinding = new RoleBinding(new ProjectId("project-1"), "roles/browser");

    var action = new RequestActivationAction(
      new LogAdapter(),
      Mocks.createRuntimeEnvironmentMock(),
      activator,
      MockitoUtils.toCdiInstance(Mocks.createNotificationServiceMock(true)),
      catalog,
      tokenSigner);

    var request = new RequestActivationAction.RequestEntity();
    request.role = roleBinding.role();
    request.peers = List.of(SAMPLE_USER_2.email, SAMPLE_USER_2.email);
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = action.execute(
      new MockIapPrincipal(SAMPLE_USER),
      "project-1",
      request,
      Mocks.createUriInfoMock());

    assertEquals(SAMPLE_USER.email, response.beneficiary.email);
    assertIterableEquals(Set.of(new UserEmail(SAMPLE_USER_2.email)), response.reviewers);
    assertTrue(response.isBeneficiary);
    assertFalse(response.isReviewer);
    assertEquals("justification", response.justification);
    assertNotNull(response.items);
    assertEquals(1, response.items.size());
    assertEquals("project-1", response.items.get(0).projectId);
    assertEquals(roleBinding, response.items.get(0).roleBinding);
    assertEquals(Entitlement.Status.ACTIVATION_PENDING, response.items.get(0).status);
    assertNotNull(response.items.get(0).activationId);
  }
}
