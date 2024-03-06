package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.web.LogAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

public class TestListProjectsAction {
  private static final UserEmail SAMPLE_USER = new UserEmail("user-1@example.com");

  @Test
  public void whenCatalogReturnsNoProjects_ThenResponseContainsEmptyList() throws Exception {
    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (catalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));
    when(catalog.listScopes(argThat(ctx -> ctx.user().equals(SAMPLE_USER))))
      .thenReturn(new TreeSet<>());

    var resource = new ListProjectsAction(new LogAdapter(), catalog);
    var response = resource.execute(new MockIapPrincipal(SAMPLE_USER));

    assertEquals(0, response.projects.size());
  }

  @Test
  public void whenCatalogReturnsProjects_ThenResponseContainsProjects() throws Exception {
    var catalog = Mockito.mock(MpaProjectRoleCatalog.class);
    when (catalog.createContext(any()))
      .thenAnswer(inv -> new MpaProjectRoleCatalog.UserContext(inv.getArgument(0)));
    when(catalog.listScopes(argThat(ctx -> ctx.user().equals(SAMPLE_USER))))
      .thenReturn(new TreeSet<>(Set.of(
        new ProjectId("project-1"),
        new ProjectId("project-2"),
        new ProjectId("project-3"))));

    var resource = new ListProjectsAction(new LogAdapter(), catalog);
    var response = resource.execute(new MockIapPrincipal(SAMPLE_USER));

    assertNotNull(response.projects);
    assertIterableEquals(
      List.of(
        "project-1",
        "project-2",
        "project-3"),
      response.projects);
  }
}
