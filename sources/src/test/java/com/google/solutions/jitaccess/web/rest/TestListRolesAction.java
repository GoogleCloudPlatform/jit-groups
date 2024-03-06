package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.catalog.EntitlementSet;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRole;
import com.google.solutions.jitaccess.web.LogAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class TestListRolesAction {
  private static final UserEmail SAMPLE_USER = new UserEmail("user-1@example.com");

  @Test
  public void whenProjectIsEmpty_ThenActionThrowsException() throws Exception {
    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (catalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));
    when(catalog.listScopes(argThat(ctx -> ctx.user().equals(SAMPLE_USER))))
      .thenThrow(new AccessDeniedException("mock"));

    var action = new ListRolesAction(new LogAdapter(), catalog);

    assertThrows(
      IllegalArgumentException.class,
      () -> action.execute(new MockIapPrincipal(SAMPLE_USER), " "));
  }

  @Test
  public void whenCatalogThrowsAccessDeniedException_ThenActionThrowsException() throws Exception {
    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (catalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));
    when(catalog
      .listEntitlements(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        eq(new ProjectId("project-1"))))
      .thenThrow(new AccessDeniedException("mock"));

    var action = new ListRolesAction(new LogAdapter(), catalog);

    assertThrows(
      AccessDeniedException.class,
      () -> action.execute(new MockIapPrincipal(SAMPLE_USER), "project-1"));
  }

  @Test
  public void whenCatalogReturnsNoRoles_ThenActionReturnsEmptyList() throws Exception {
    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (catalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));
    when(catalog
      .listEntitlements(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        eq(new ProjectId("project-1"))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of()),
        new TreeSet<>(Set.of()),
        Set.of("warning")));

    var action = new ListRolesAction(new LogAdapter(), catalog);
    var response = action.execute(new MockIapPrincipal(SAMPLE_USER), "project-1");

    assertNotNull(response.roles);
    assertEquals(0, response.roles.size());
    assertNotNull(response.warnings);
    assertEquals(1, response.warnings.size());
    assertEquals("warning", response.warnings.stream().findFirst().get());
  }

  @Test
  public void whenCatalogReturnsRoles_ThenActionReturnsList() throws Exception {
    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (catalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));

    var role1 = new Entitlement<ProjectRole>(
      new ProjectRole(new RoleBinding(new ProjectId("project-1").getFullResourceName(), "roles/browser")),
      "ent-1",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);
    var role2 = new Entitlement<ProjectRole>(
      new ProjectRole(new RoleBinding(new ProjectId("project-1").getFullResourceName(), "roles/janitor")),
      "ent-2",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);

    when(catalog
      .listEntitlements(
        argThat(ctx -> ctx.user().equals(SAMPLE_USER)),
        eq(new ProjectId("project-1"))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(role1, role2)),
        new TreeSet<>(),
        Set.of()));

    var action = new ListRolesAction(new LogAdapter(), catalog);
    var response = action.execute(new MockIapPrincipal(SAMPLE_USER), "project-1");

    assertNotNull(response.roles);
    assertEquals(2, response.roles.size());
    assertEquals(role1.id().roleBinding(), response.roles.get(0).roleBinding);
    assertEquals(role2.id().roleBinding(), response.roles.get(1).roleBinding);
    assertTrue(response.warnings.isEmpty());
  }
}
