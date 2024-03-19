package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRole;
import com.google.solutions.jitaccess.web.actions.*;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;

@Dependent
@Path("/api/")
public class ProjectApiResource extends AbstractApiResource<ProjectId> {

  @Inject
  MetadataAction<
    ProjectRole,
    ProjectId,
    MpaProjectRoleCatalog.UserContext> metadataAction;

  @Inject
  ListProjectsAction listProjectsAction;

  @Inject
  ListRolesAction listRolesAction;

  @Inject
  ListPeersAction listPeersAction;

  @Inject
  RequestAndSelfApproveAction requestAndSelfApproveAction;

  @Inject
  RequestActivationAction requestActivationAction;

  @Inject
  IntrospectActivationRequestAction introspectActivationRequestAction;

  @Inject
  ApproveActivationRequestAction approveActivationRequestAction;

  // -------------------------------------------------------------------------
  // Overrides.
  // -------------------------------------------------------------------------

  public MetadataAction metadataAction() {
    return metadataAction;
  }

  public ListProjectsAction listScopesAction() {
    return listProjectsAction;
  }

  public ListRolesAction listRolesAction() {
    return listRolesAction;
  }

  public ListPeersAction listPeersAction() {
    return listPeersAction;
  }

  public RequestAndSelfApproveAction requestAndSelfApproveAction() {
    return requestAndSelfApproveAction;
  }

  public RequestActivationAction requestActivationAction() {
    return requestActivationAction;
  }

  public IntrospectActivationRequestAction introspectActivationRequestAction() {
    return introspectActivationRequestAction;
  }

  public ApproveActivationRequestAction approveActivationRequestAction() {
    return approveActivationRequestAction;
  }
}
