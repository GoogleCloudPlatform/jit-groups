package com.google.solutions.jitaccess.web.rest;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.Exceptions;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.LogEvents;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * List peers that are qualified to approve the activation of a role.
 */
public class ListPeersAction extends AbstractAction {
  private final @NotNull MpaProjectRoleCatalog catalog;

  public ListPeersAction(
    @NotNull LogAdapter logAdapter,
    @NotNull MpaProjectRoleCatalog catalog
  ) {
    super(logAdapter);
    this.catalog = catalog;
  }

  public @NotNull ResponseEntity execute(
    @NotNull IapPrincipal iapPrincipal,
    @Nullable String projectIdString,
    @Nullable String role
  ) throws AccessException {
    Preconditions.checkArgument(
      projectIdString != null && !projectIdString.trim().isEmpty(),
      "A projectId is required");
    Preconditions.checkArgument(
      role != null && !role.trim().isEmpty(),
      "A role is required");

    var userContext = this.catalog.createContext(iapPrincipal.email());

    var projectId = new ProjectId(projectIdString);
    var roleBinding = new RoleBinding(projectId, role);

    try {
      var peers = this.catalog.listReviewers(
        userContext,
        new com.google.solutions.jitaccess.core.catalog.project.ProjectRole(roleBinding));

      assert !peers.contains(iapPrincipal.email());

      return new ResponseEntity(peers);
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_LIST_PEERS,
          String.format("Listing peers failed: %s", Exceptions.getFullMessage(e)))
        .addLabels(le -> addLabels(le, e))
        .addLabels(le -> addLabels(le, roleBinding))
        .addLabels(le -> addLabels(le, projectId))
        .write();

      throw new AccessDeniedException("Listing peers failed, see logs for details");
    }
  }


  public static class ResponseEntity {
    public final @NotNull Set<UserEmail> peers;

    private ResponseEntity(@NotNull Set<UserEmail> peers) {
      Preconditions.checkNotNull(peers);
      this.peers = peers;
    }
  }
}
