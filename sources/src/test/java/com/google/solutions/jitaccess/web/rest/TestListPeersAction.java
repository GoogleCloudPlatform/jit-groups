package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.web.LogAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

public class TestListPeersAction {
  private static final UserEmail SAMPLE_USER = new UserEmail("user-1@example.com");

  @Test
  public void whenCatalogThrowsAccessDeniedException_ThenActionThrowsException() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog.listReviewers(argThat(ctx -> ctx.user().equals(SAMPLE_USER)), any()))
      .thenThrow(new AccessDeniedException("mock"));

    var action = new ListPeersAction(new LogAdapter(), catalog);

    assertThrows(
      AccessDeniedException.class,
      () -> action.execute(new MockIapPrincipal(SAMPLE_USER), "project-1", "roles/browser"));
  }

  @Test
  public void whenCatalogReturnsNoPeers_ThenActionReturnsEmptyList() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog
      .listReviewers(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        argThat(r -> r.roleBinding().role().equals("roles/browser"))))
      .thenReturn(new TreeSet());

    var action = new ListPeersAction(new LogAdapter(), catalog);
    var response = action.execute(new MockIapPrincipal(SAMPLE_USER), "project-1", "roles/browser");

    assertNotNull(response.peers);
    assertEquals(0, response.peers.size());
  }

  @Test
  public void whenCatalogReturnsProjects_ThenActionReturnsList() throws Exception {
    var catalog = Mocks.createMpaProjectRoleCatalogMock();
    when(catalog
      .listReviewers(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        argThat(r -> r.roleBinding().role().equals("roles/browser"))))
      .thenReturn(new TreeSet(Set.of(new UserEmail("peer-1@example.com"), new UserEmail("peer-2@example.com"))));

    var action = new ListPeersAction(new LogAdapter(), catalog);
    var response = action.execute(new MockIapPrincipal(SAMPLE_USER), "project-1", "roles/browser");

    assertNotNull(response.peers);
    assertEquals(2, response.peers.size());
  }
}
