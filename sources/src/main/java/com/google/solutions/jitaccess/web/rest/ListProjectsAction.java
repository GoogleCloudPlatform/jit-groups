package com.google.solutions.jitaccess.web.rest;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.Exceptions;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.LogEvents;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import jakarta.enterprise.context.Dependent;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * List projects that the calling user can access.
 */
@Dependent
public class ListProjectsAction extends AbstractAction { //TODO: rename to scopes
  private final @NotNull MpaProjectRoleCatalog mpaCatalog;

  public ListProjectsAction(
    @NotNull LogAdapter logAdapter,
    @NotNull MpaProjectRoleCatalog mpaCatalog
  ) {
    super(logAdapter);
    this.mpaCatalog = mpaCatalog;
  }

  public @NotNull ListProjectsAction.ResponseEntity execute(
    @NotNull IapPrincipal iapPrincipal
  ) throws AccessException {
    Preconditions.checkNotNull(this.mpaCatalog, "catalog");

    var userContext = this.mpaCatalog.createContext(iapPrincipal.email());

    try {
      var projects = this.mpaCatalog.listScopes(userContext);

      this.logAdapter
        .newInfoEntry(
          LogEvents.API_LIST_PROJECTS,
          String.format("Found %d available projects", projects.size()))
        .write();

      return new ResponseEntity(projects
        .stream()
        .map(ProjectId::id)
        .collect(Collectors.toCollection(TreeSet::new)));
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_LIST_PROJECTS,
          String.format("Listing available projects failed: %s", Exceptions.getFullMessage(e)))
        .write();

      throw new AccessDeniedException("Listing available projects failed, see logs for details");
    }
  }

  public static class ResponseEntity { // TODO: make record
    public final @NotNull Set<String> projects;

    private ResponseEntity(@NotNull SortedSet<String> projects) {
      Preconditions.checkNotNull(projects, "projects");
      this.projects = projects;
    }
  }

}
