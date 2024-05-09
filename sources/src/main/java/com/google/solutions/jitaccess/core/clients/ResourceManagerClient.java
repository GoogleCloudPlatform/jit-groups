//
// Copyright 2021 Google LLC
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.google.solutions.jitaccess.core.clients;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Key;
import com.google.api.services.cloudresourcemanager.v3.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.v3.CloudResourceManagerRequest;
import com.google.api.services.cloudresourcemanager.v3.model.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.cel.TemporaryIamCondition;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.catalog.FolderId;
import com.google.solutions.jitaccess.core.catalog.OrganizationId;
import com.google.solutions.jitaccess.core.catalog.ProjectId;
import com.google.solutions.jitaccess.core.catalog.ResourceId;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Adapter for Resource Manager API.
 */
@Singleton
public class ResourceManagerClient {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  private static final int MAX_SET_IAM_POLICY_ATTEMPTS = 4;

  private static final int SEARCH_PROJECTS_PAGE_SIZE = 1000;

  private final @NotNull GoogleCredentials credentials;
  private final @NotNull HttpTransport.Options httpOptions;

  private @NotNull CloudResourceManager createClient() throws IOException
  {
    try {
      return new CloudResourceManager
        .Builder(
          HttpTransport.newTransport(),
          new GsonFactory(),
          HttpTransport.newAuthenticatingRequestInitializer(this.credentials, this.httpOptions))
        .setApplicationName(ApplicationVersion.USER_AGENT)
        .build();
    }
    catch (GeneralSecurityException e) {
      throw new IOException("Creating a ResourceManager client failed", e);
    }
  }

  private static boolean isRoleNotGrantableErrorMessage(@Nullable String message)
  {
    return message != null &&
      (message.contains("not supported") || message.contains("does not exist"));
  }

  public ResourceManagerClient(
    @NotNull GoogleCredentials credentials,
    @NotNull HttpTransport.Options httpOptions
  ) {
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.credentials = credentials;
    this.httpOptions = httpOptions;
  }

  /**
   * Add an IAM binding using the optimistic concurrency control-mechanism.
   */
  public void addProjectIamBinding(
    @NotNull ProjectId projectId,
    @NotNull Binding binding,
    @NotNull EnumSet<ResourceManagerClient.IamBindingOptions> options,
    String requestReason
  ) throws AccessException, AlreadyExistsException, IOException {
    Preconditions.checkNotNull(projectId, "projectId");
    Preconditions.checkNotNull(binding, "binding");

    try {
      var service = createClient();

      //
      // IAM policies use optimistic concurrency control, so we might need to perform
      // multiple attempts to update the policy.
      //
      for (int attempt = 0; attempt < MAX_SET_IAM_POLICY_ATTEMPTS; attempt++) {
        //
        // Read current version of policy.
        //
        // NB. The API might return a v1 policy even if we
        // request a v3 policy.
        //

        var policy = service
          .projects()
          .getIamPolicy(
            String.format("projects/%s", projectId.id()),
            new GetIamPolicyRequest()
              .setOptions(new GetPolicyOptions().setRequestedPolicyVersion(3)))
          .execute();

        //
        // Make sure we're using v3; older versions don't support conditions.
        //
        policy.setVersion(3);

        if (options.contains(IamBindingOptions.FAIL_IF_BINDING_EXISTS)) {
          if (policy.getBindings()
            .stream()
            .anyMatch(b -> Bindings.equals(b, binding, true))) {
            //
            // The exact same binding (incl. condition) exists.
            //
            throw new AlreadyExistsException("The binding already exists");
          }
        }

        if (options.contains(ResourceManagerClient.IamBindingOptions.PURGE_EXISTING_TEMPORARY_BINDINGS)) {
          //
          // Remove existing temporary bindings for the same principal and role.
          //
          // NB. There's a hard limit on how many role bindings in a policy can
          // have the same principal and role. Removing existing bindings
          // helps avoid hitting this limit.
          //
          // NB. This check detects temporary bindings that we created, but it might not
          // detect other temporary bindings (which might use a slightly different
          // condition)
          //
          Predicate<Binding> isObsolete = b -> Bindings.equals(b, binding, false)
            && b.getCondition() != null
            && TemporaryIamCondition.isTemporaryAccessCondition(b.getCondition().getExpression());

          var nonObsoleteBindings =
            policy.getBindings().stream()
              .filter(isObsolete.negate())
              .toList();

          policy.getBindings().clear();
          policy.getBindings().addAll(nonObsoleteBindings);
        }

        //
        // Apply change and write new version.
        //
        policy.getBindings().add(binding);

        try {
          var request = service
            .projects()
            .setIamPolicy(
              String.format("projects/%s", projectId),
              new SetIamPolicyRequest().setPolicy((policy)));

          request.getRequestHeaders().set("x-goog-request-reason", requestReason);
          request.execute();

          //
          // Successful update -> quit loop.
          //
          return;
        }
        catch (GoogleJsonResponseException e) {
          if (e.getStatusCode() == 412) {
            //
            // Concurrent modification - back off and retry.
            //
            try {
              Thread.sleep(200);
            }
            catch (InterruptedException ignored) {
            }
          }
          else {
            throw (GoogleJsonResponseException) e.fillInStackTrace();
          }
        }
      }

      throw new AlreadyExistsException(
        "Failed to update IAM bindings due to concurrent modifications");
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 400:
          //
          // One possible reason for an INVALID_ARGUMENT error is that we've tried
          // to grant a role on a project that cannot be granted on a project at all.
          // If that's the case, provide a more descriptive error message.
          //
          if (e.getDetails() != null &&
              e.getDetails().getErrors() != null &&
              e.getDetails().getErrors().size() > 0 &&
              isRoleNotGrantableErrorMessage(e.getDetails().getErrors().get(0).getMessage())) {
            throw new AccessDeniedException(
              String.format("The role %s cannot be granted on a project", binding.getRole()),
              e);
          }
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(String.format("Denied access to project '%s'", projectId), e);
        default:
          throw (GoogleJsonResponseException) e.fillInStackTrace();
      }
    }
  }

  /**
   * Test whether certain permissions have been granted on the project.
   */
  public @NotNull List<String> testIamPermissions(
    @NotNull ProjectId projectId,
    @NotNull List<String> permissions
  ) throws NotAuthenticatedException, IOException {
    try
    {
      var response = createClient()
        .projects()
        .testIamPermissions(
          String.format("projects/%s", projectId),
          new TestIamPermissionsRequest()
            .setPermissions(permissions))
        .execute();

      return response.getPermissions() != null
        ? response.getPermissions()
        : List.of();
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        default:
          throw (GoogleJsonResponseException) e.fillInStackTrace();
      }
    }
  }

  /**
   * Search for projects.
   */
  public @NotNull SortedSet<ProjectId> searchProjectIds(
    @NotNull String query
  ) throws NotAuthenticatedException, IOException {
    try {
      var client = createClient();

      var response = client
        .projects()
        .search()
        .setQuery(query)
        .setPageSize(SEARCH_PROJECTS_PAGE_SIZE)
        .execute();

      ArrayList<Project> allProjects = new ArrayList<>();
      if(response.getProjects() != null) {
        allProjects.addAll(response.getProjects());
      }

      while(response.getNextPageToken() != null
              && !response.getNextPageToken().isEmpty()
              && response.getProjects() !=null
              && response.getProjects().size() >= SEARCH_PROJECTS_PAGE_SIZE) {
        response = client
          .projects()
          .search()
          .setQuery(query)
          .setPageToken(response.getNextPageToken())
          .setPageSize(SEARCH_PROJECTS_PAGE_SIZE)
          .execute();

        if(response.getProjects() != null) {
          allProjects.addAll(response.getProjects());
        }
      }

      return allProjects.stream()
        .map(p -> new ProjectId(p.getProjectId()))
        .collect(Collectors.toCollection(TreeSet::new));
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        default:
          throw (GoogleJsonResponseException) e.fillInStackTrace();
      }
    }
  }

  /**
   * Get the ancestry of a project.
   *
   * @return list of ancestors, starting with the project itself.
   */
  public @NotNull Collection<ResourceId> getAncestry(
    @NotNull ProjectId projectId
  ) throws AccessException, IOException {
    try {
      var response = new GetAncestry(createClient(), projectId.id(), new GetAncestryRequest()).execute();
      return response.ancestor
        .stream()
        .map(a -> {
          switch (a.resourceId.type) {
            case "organization":
              return (ResourceId)new OrganizationId(a.resourceId.id);

            case "folder":
              return new FolderId(a.resourceId.id);

            case "project":
              return new ProjectId(a.resourceId.id);

            default:
              throw new IllegalArgumentException(
                String.format("Unknown resource type: %s", a.resourceId.type));
          }
        })
        .collect(Collectors.toList());
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(String.format("Denied access to project '%s'", projectId), e);
        default:
          throw (GoogleJsonResponseException) e.fillInStackTrace();
      }
    }
  }

  //---------------------------------------------------------------------
  // Inner classes.
  //---------------------------------------------------------------------

  /**
   * Helper class for using Binding objects.
   */
  public static class Bindings {
    public static boolean equals(@NotNull Binding lhs, @NotNull Binding rhs, boolean compareCondition) {
      if (!lhs.getRole().equals(rhs.getRole())) {
        return  false;
      }

      if (!new HashSet<>(lhs.getMembers()).equals(new HashSet<>(rhs.getMembers()))) {
        return false;
      }

      if (compareCondition) {
        if ((lhs.getCondition() == null) != (rhs.getCondition() == null)) {
          return false;
        }

        if (lhs.getCondition() != null && rhs.getCondition() != null) {
          if (!Objects.equals(lhs.getCondition().getExpression(), rhs.getCondition().getExpression())) {
            return false;
          }

          if (!Objects.equals(lhs.getCondition().getTitle(), rhs.getCondition().getTitle())) {
            return false;
          }

          return Objects.equals(lhs.getCondition().getDescription(), rhs.getCondition().getDescription());
        }
      }

      return true;
    }
  }

  public enum IamBindingOptions {
    NONE,

    /** Purge existing temporary bindings for the same principal and role */
    PURGE_EXISTING_TEMPORARY_BINDINGS,

    /** Throw an AlreadyExistsException if an equivalent binding for the same principal and role exists */
    FAIL_IF_BINDING_EXISTS
  }

  //---------------------------------------------------------------------------
  // Request classes for APIs only available in v1.
  //---------------------------------------------------------------------------

  /**
   * Gets a list of ancestors in the resource hierarchy for the Project identified by the specified
   * `project_id` (for example, `my-project-123`). The caller must have read permissions for this
   * Project.
   */
  private static class GetAncestry extends CloudResourceManagerRequest<GetAncestryResponse> {

    private static final String REST_PATH = "v1/projects/{projectId}:getAncestry";

    /**
     * Gets a list of ancestors in the resource hierarchy for the Project identified by the specified
     * `project_id` (for example, `my-project-123`). The caller must have read permissions for this
     * Project.
     */
    protected GetAncestry(
      @NotNull CloudResourceManager client,
      String projectId,
      GetAncestryRequest content
    ) {
      super(client, "POST", REST_PATH, content, GetAncestryResponse.class);
      this.projectId = Preconditions.checkNotNull(projectId, "Required parameter projectId must be specified.");
    }

    @Override
    public GetAncestry set$Xgafv(String $Xgafv) {
      return (GetAncestry) super.set$Xgafv($Xgafv);
    }

    @Override
    public GetAncestry setAccessToken(String accessToken) {
      return (GetAncestry) super.setAccessToken(accessToken);
    }

    @Override
    public GetAncestry setAlt(String alt) {
      return (GetAncestry) super.setAlt(alt);
    }

    @Override
    public GetAncestry setCallback(String callback) {
      return (GetAncestry) super.setCallback(callback);
    }

    @Override
    public GetAncestry setFields(String fields) {
      return (GetAncestry) super.setFields(fields);
    }

    @Override
    public GetAncestry setKey(String key) {
      return (GetAncestry) super.setKey(key);
    }

    @Override
    public GetAncestry setOauthToken(String oauthToken) {
      return (GetAncestry) super.setOauthToken(oauthToken);
    }

    @Override
    public GetAncestry setPrettyPrint(Boolean prettyPrint) {
      return (GetAncestry) super.setPrettyPrint(prettyPrint);
    }

    @Override
    public GetAncestry setQuotaUser(String quotaUser) {
      return (GetAncestry) super.setQuotaUser(quotaUser);
    }

    @Override
    public GetAncestry setUploadType(String uploadType) {
      return (GetAncestry) super.setUploadType(uploadType);
    }

    @Override
    public GetAncestry setUploadProtocol(String uploadProtocol) {
      return (GetAncestry) super.setUploadProtocol(uploadProtocol);
    }

    /** Required. The Project ID (for example, `my-project-123`). */
    @Key
    private String projectId;

    /** Required. The Project ID (for example, `my-project-123`).
     */
    public String getProjectId() {
      return projectId;
    }

    /** Required. The Project ID (for example, `my-project-123`). */
    public @NotNull GetAncestry setProjectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    @Override
    public GetAncestry set(String parameterName, Object value) {
      return (GetAncestry) super.set(parameterName, value);
    }
  }

  /**
   * The request sent to the GetAncestry method.
   */
  private static final class GetAncestryRequest extends com.google.api.client.json.GenericJson {
  }

  /**
   * Response from the projects.getAncestry method.
   */
  public static final class GetAncestryResponse extends GenericJson {
    /**
     * Ancestors are ordered from bottom to top of the resource hierarchy. The first ancestor is the
     * project itself, followed by the project's parent, etc.
     * The value may be {@code null}.
     */
    @Key
    private java.util.List<Ancestor> ancestor;

    /**
     * Ancestors are ordered from bottom to top of the resource hierarchy. The first ancestor is the
     * project itself, followed by the project's parent, etc..
     * @return value or {@code null} for none
     */
    public java.util.List<Ancestor> getAncestor() {
      return ancestor;
    }

    /**
     * Ancestors are ordered from bottom to top of the resource hierarchy. The first ancestor is the
     * project itself, followed by the project's parent, etc..
     * @param ancestor ancestor or {@code null} for none
     */
    public @NotNull GetAncestryResponse setAncestor(java.util.List<Ancestor> ancestor) {
      this.ancestor = ancestor;
      return this;
    }

    @Override
    public GetAncestryResponse set(String fieldName, Object value) {
      return (GetAncestryResponse) super.set(fieldName, value);
    }

    @Override
    public GetAncestryResponse clone() {
      return (GetAncestryResponse) super.clone();
    }
  }

  /**
   * Identifying information for a single ancestor of a project.
   */
  public static final class Ancestor extends com.google.api.client.json.GenericJson {
    /**
     * Resource id of the ancestor.
     * The value may be {@code null}.
     */
    @Key
    private AncestryResourceId resourceId;

    /**
     * Resource id of the ancestor.
     * @return value or {@code null} for none
     */
    public AncestryResourceId getResourceId() {
      return resourceId;
    }

    /**
     * Resource id of the ancestor.
     * @param resourceId resourceId or {@code null} for none
     */
    public @NotNull Ancestor setResourceId(AncestryResourceId resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    @Override
    public Ancestor set(String fieldName, Object value) {
      return (Ancestor) super.set(fieldName, value);
    }

    @Override
    public Ancestor clone() {
      return (Ancestor) super.clone();
    }
  }

  /**
   * A container to reference an id for any resource type.
   */
  public static final class AncestryResourceId extends com.google.api.client.json.GenericJson {
    /**
     * The type-specific id. This should correspond to the id used in the type-specific API's.
     * The value may be {@code null}.
     */
    @Key
    private String id;

    /**
     * The resource type this id is for. At present, the valid types are: "organization", "folder",
     * and "project".
     * The value may be {@code null}.
     */
    @Key
    private String type;

    /**
     * The type-specific id. This should correspond to the id used in the type-specific API's.
     * @return value or {@code null} for none
     */
    public String getId() {
      return id;
    }

    /**
     * The type-specific id. This should correspond to the id used in the type-specific API's.
     * @param id id or {@code null} for none
     */
    public @NotNull AncestryResourceId setId(String id) {
      this.id = id;
      return this;
    }

    /**
     * The resource type this id is for. At present, the valid types are: "organization", "folder",
     * and "project".
     * @return value or {@code null} for none
     */
    public String getType() {
      return type;
    }

    /**
     * The resource type this id is for. At present, the valid types are: "organization", "folder",
     * and "project".
     * @param type type or {@code null} for none
     */
    public @NotNull AncestryResourceId setType(String type) {
      this.type = type;
      return this;
    }

    @Override
    public AncestryResourceId set(String fieldName, Object value) {
      return (AncestryResourceId) super.set(fieldName, value);
    }

    @Override
    public AncestryResourceId clone() {
      return (AncestryResourceId) super.clone();
    }
  }
}
