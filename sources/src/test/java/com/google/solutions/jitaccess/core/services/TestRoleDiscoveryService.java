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

package com.google.solutions.jitaccess.core.services;

import com.google.api.services.cloudasset.v1.model.*;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.adapters.AssetInventoryAdapter;
import com.google.solutions.jitaccess.core.data.ProjectId;
import com.google.solutions.jitaccess.core.data.ProjectRole;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

public class TestRoleDiscoveryService {
  private static final UserId SAMPLE_USER = new UserId("user-1", "user-1@example.com");
  private static final UserId SAMPLE_USER_2 = new UserId("user-2", "user-2@example.com");
  private static final UserId SAMPLE_USER_3 = new UserId("user-2", "user-3@example.com");
  private static final ProjectId SAMPLE_PROJECT_ID_1 = new ProjectId("project-1");
  private static final ProjectId SAMPLE_PROJECT_ID_2 = new ProjectId("project-2");
  private static final String SAMPLE_PROJECT_RESOURCE_1 = "//cloudresourcemanager.googleapis.com/projects/project-1";
  private static final String SAMPLE_PROJECT_RESOURCE_2 = "//cloudresourcemanager.googleapis.com/projects/project-2";
  private static final String SAMPLE_ROLE = "roles/resourcemanager.role1";
  private static final String SAMPLE_ROLE_2 = "roles/resourcemanager.role2";
  private static final String JIT_CONDITION = "has({}.jitAccessConstraint)";
  private static final String MPA_CONDITION = "has({}.multiPartyApprovalConstraint)";

  private static IamPolicyAnalysisResult createIamPolicyAnalysisResult(
    String resource,
    String role,
    UserId user
  ) {
    return new IamPolicyAnalysisResult()
      .setAttachedResourceFullName(resource)
      .setAccessControlLists(List.of(new GoogleCloudAssetV1AccessControlList()
        .setResources(List.of(new GoogleCloudAssetV1Resource()
          .setFullResourceName(resource)))))
      .setIamBinding(new Binding()
        .setMembers(List.of("user:" + user))
        .setRole(role));
  }

  private static IamPolicyAnalysisResult createConditionalIamPolicyAnalysisResult(
    String resource,
    String role,
    UserId user,
    String condition,
    String conditionTitle,
    String evaluationResult
  ) {
    return new IamPolicyAnalysisResult()
      .setAttachedResourceFullName(resource)
      .setAccessControlLists(List.of(new GoogleCloudAssetV1AccessControlList()
        .setResources(List.of(new GoogleCloudAssetV1Resource()
          .setFullResourceName(resource)))
        .setConditionEvaluation(new ConditionEvaluation()
          .setEvaluationValue(evaluationResult))))
      .setIamBinding(new Binding()
        .setMembers(List.of("user:" + user))
        .setRole(role)
        .setCondition(new Expr()
          .setTitle(conditionTitle)
          .setExpression(condition)))
      .setIdentityList(new GoogleCloudAssetV1IdentityList()
        .setIdentities(List.of(
          new GoogleCloudAssetV1Identity().setName("user:" + user.email),
          new GoogleCloudAssetV1Identity().setName("serviceAccount:ignoreme@x.iam.gserviceaccount.com"),
          new GoogleCloudAssetV1Identity().setName("group:ignoreme@example.com"))));
  }

  // ---------------------------------------------------------------------
  // listAvailableProjects.
  // ---------------------------------------------------------------------

  @Test
  public void whenAnalysisResultEmpty_ThenListAvailableProjectsReturnsEmptyList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.of("resourcemanager.projects.get")),
        eq(Optional.empty()),
        eq(true)))
      .thenReturn(new IamPolicyAnalysis());

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var projectIds = service.listAvailableProjects(SAMPLE_USER);
    assertNotNull(projectIds);
    assertEquals(0, projectIds.size());
  }

  @Test
  public void whenAnalysisResultContainsAcsWithUnrecognizedConditions_ThenListAvailableProjectsReturnsEmptyList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.of("resourcemanager.projects.get")),
        eq(Optional.empty()),
        eq(true)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE,
            SAMPLE_USER,
            "a==b",
            "unrecognized condition",
            "TRUE"))));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var projectIds = service.listAvailableProjects(SAMPLE_USER);
    assertNotNull(projectIds);
    assertEquals(0, projectIds.size());
  }

  @Test
  public void whenAnalysisContainsPermanentBinding_ThenListAvailableProjectsReturnsProjectId()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.of("resourcemanager.projects.get")),
        eq(Optional.empty()),
        eq(true)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createIamPolicyAnalysisResult(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE,
            SAMPLE_USER))));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var projectIds = service.listAvailableProjects(SAMPLE_USER);
    assertNotNull(projectIds);
    assertEquals(1, projectIds.size());
    assertTrue(projectIds.contains(SAMPLE_PROJECT_ID_1));
  }

  @Test
  public void whenAnalysisContainsEligibleBindings_ThenListAvailableProjectsReturnsProjectIds()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.of("resourcemanager.projects.get")),
        eq(Optional.empty()),
        eq(true)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE,
            SAMPLE_USER,
            JIT_CONDITION,
            "eligible binding",
            "CONDITIONAL"),
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_RESOURCE_2,
            SAMPLE_ROLE,
            SAMPLE_USER,
            MPA_CONDITION,
            "eligible binding",
            "CONDITIONAL"))));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var projectIds = service.listAvailableProjects(SAMPLE_USER);
    assertNotNull(projectIds);
    assertEquals(2, projectIds.size());
    assertTrue(projectIds.contains(SAMPLE_PROJECT_ID_1));
    assertTrue(projectIds.contains(SAMPLE_PROJECT_ID_2));
  }

  // ---------------------------------------------------------------------
  // listEligibleProjectRoles.
  // ---------------------------------------------------------------------

  @Test
  public void whenAnalysisResultEmpty_ThenListEligibleProjectRolesReturnsEmptyList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis());

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(SAMPLE_USER, SAMPLE_PROJECT_ID_1);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(0, roles.getItems().size());
  }

  @Test
  public void whenAnalysisResultContainsEmptyAcl_ThenListEligibleProjectRolesReturnsEmptyList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          new IamPolicyAnalysisResult().setAttachedResourceFullName(SAMPLE_PROJECT_RESOURCE_1))));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(SAMPLE_USER, SAMPLE_PROJECT_ID_1);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(0, roles.getItems().size());
  }

  @Test
  public void whenAnalysisContainsNoEligibleRoles_ThenListEligibleProjectRolesReturnsEmptyList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createIamPolicyAnalysisResult(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE,
            SAMPLE_USER))));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(SAMPLE_USER, SAMPLE_PROJECT_ID_1);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(0, roles.getItems().size());
  }

  @Test
  public void whenAnalysisContainsJitEligibleBinding_ThenListEligibleProjectRolesReturnsList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE,
            SAMPLE_USER,
            JIT_CONDITION,
            "eligible binding",
            "CONDITIONAL"))));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(SAMPLE_USER, SAMPLE_PROJECT_ID_1);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(1, roles.getItems().size());

    var role = roles.getItems().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, role.getProjectId());
    assertEquals(SAMPLE_ROLE, role.roleBinding.role);
    assertEquals(ProjectRole.Status.ELIGIBLE_FOR_JIT, role.status);
  }

  @Test
  public void whenAnalysisContainsDuplicateJitEligibleBinding_ThenListEligibleProjectRolesReturnsList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE,
            SAMPLE_USER,
            JIT_CONDITION,
            "eligible binding #1",
            "CONDITIONAL"),
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE,
            SAMPLE_USER,
            JIT_CONDITION,
            "eligible binding #2",
            "CONDITIONAL"))));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(SAMPLE_USER, SAMPLE_PROJECT_ID_1);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(1, roles.getItems().size());

    var role = roles.getItems().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, role.getProjectId());
    assertEquals(SAMPLE_ROLE, role.roleBinding.role);
    assertEquals(ProjectRole.Status.ELIGIBLE_FOR_JIT, role.status);
  }

  @Test
  public void whenAnalysisContainsMpaEligibleBinding_ThenListEligibleProjectRolesReturnsList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE,
            SAMPLE_USER,
            MPA_CONDITION,
            "eligible binding",
            "CONDITIONAL"))));

    var service = new RoleDiscoveryService(
        assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(SAMPLE_USER, SAMPLE_PROJECT_ID_1);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(1, roles.getItems().size());

    var role = roles.getItems().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, role.getProjectId());
    assertEquals(SAMPLE_ROLE, role.roleBinding.role);
    assertEquals(ProjectRole.Status.ELIGIBLE_FOR_MPA, role.status);
  }

  @Test
  public void whenAnalysisContainsDuplicateMpaEligibleBinding_ThenListEligibleProjectRolesReturnsList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE,
            SAMPLE_USER,
            MPA_CONDITION,
            "eligible binding # 1",
            "CONDITIONAL"),
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE,
            SAMPLE_USER,
            MPA_CONDITION,
            "eligible binding # 2",
            "CONDITIONAL"))));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(SAMPLE_USER, SAMPLE_PROJECT_ID_1);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(1, roles.getItems().size());

    var role = roles.getItems().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, role.getProjectId());
    assertEquals(SAMPLE_ROLE, role.roleBinding.role);
    assertEquals(ProjectRole.Status.ELIGIBLE_FOR_MPA, role.status);
  }

  @Test
  public void whenAnalysisContainsMpaEligibleBindingAndJitEligibleBindingForDifferentRoles_ThenListEligibleProjectRolesReturnsList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var jitEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      JIT_CONDITION,
      "JIT-eligible binding",
      "CONDITIONAL");

    var mpaEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE_2,
      SAMPLE_USER,
      MPA_CONDITION,
      "MPA-eligible binding",
      "CONDITIONAL");

    when(assetAdapter.findAccessibleResourcesByUser(
      anyString(),
      eq(SAMPLE_USER),
      eq(Optional.empty()),
      eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
      eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(jitEligibleBinding, mpaEligibleBinding)));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(SAMPLE_USER, SAMPLE_PROJECT_ID_1);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(2, roles.getItems().size());

    var role = roles.getItems().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, role.getProjectId());
    assertEquals(SAMPLE_ROLE, role.roleBinding.role);
    assertEquals(ProjectRole.Status.ELIGIBLE_FOR_JIT, role.status);

    role = roles.getItems().stream().skip(1).findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, role.getProjectId());
    assertEquals(SAMPLE_ROLE_2, role.roleBinding.role);
    assertEquals(ProjectRole.Status.ELIGIBLE_FOR_MPA, role.status);
  }

  @Test
  public void whenAnalysisContainsMpaEligibleBindingAndJitEligibleBindingForSameRole_ThenListEligibleProjectRolesReturnsList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var jitEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      JIT_CONDITION,
      "JIT-eligible binding",
      "CONDITIONAL");

    var mpaEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      MPA_CONDITION,
      "MPA-eligible binding",
      "CONDITIONAL");

    when(assetAdapter.findAccessibleResourcesByUser(
      anyString(),
      eq(SAMPLE_USER),
      eq(Optional.empty()),
      eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
      eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(jitEligibleBinding, mpaEligibleBinding)));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(SAMPLE_USER, SAMPLE_PROJECT_ID_1);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(1, roles.getItems().size());

    // Only the JIT-eligible binding is retained.
    var role = roles.getItems().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, role.getProjectId());
    assertEquals(SAMPLE_ROLE, role.roleBinding.role);
    assertEquals(ProjectRole.Status.ELIGIBLE_FOR_JIT, role.status);
  }

  @Test
  public void whenAnalysisContainsActivatedBinding_ThenListEligibleProjectRolesReturnsMergedList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var eligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      JIT_CONDITION,
      "eligible binding",
      "CONDITIONAL");

    var activatedBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      "time ...",
      JitConstraints.ACTIVATION_CONDITION_TITLE,
      "TRUE");

    var activatedExpiredBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      "time ...",
      JitConstraints.ACTIVATION_CONDITION_TITLE,
      "FALSE");

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          eligibleBinding,
          activatedBinding,
          activatedExpiredBinding
        )));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(SAMPLE_USER, SAMPLE_PROJECT_ID_1);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(1, roles.getItems().size());

    var role = roles.getItems().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, role.getProjectId());
    assertEquals(SAMPLE_ROLE, role.roleBinding.role);
    assertEquals(ProjectRole.Status.ACTIVATED, role.status);
  }

  @Test
  public void whenAnalysisContainsEligibleBindingWithExtraCondition_ThenBindingIsIgnored()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_RESOURCE_1,
            SAMPLE_ROLE,
            SAMPLE_USER,
            JIT_CONDITION + " && resource.name=='Foo'",
            "eligible binding with extra junk",
            "CONDITIONAL"))));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(SAMPLE_USER, SAMPLE_PROJECT_ID_1);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(0, roles.getItems().size());
  }

  @Test
  public void whenAnalysisContainsInheritedEligibleBinding_ThenListEligibleProjectRolesAsyncReturnsList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var parentFolderAcl = new GoogleCloudAssetV1AccessControlList()
      .setResources(List.of(new GoogleCloudAssetV1Resource()
        .setFullResourceName("//cloudresourcemanager.googleapis.com/folders/folder-1")))
      .setConditionEvaluation(new ConditionEvaluation()
        .setEvaluationValue("CONDITIONAL"));

    var childFolderAndProjectAcl = new GoogleCloudAssetV1AccessControlList()
      .setResources(List.of(
        new GoogleCloudAssetV1Resource()
          .setFullResourceName("//cloudresourcemanager.googleapis.com/folders/folder-1"),
        new GoogleCloudAssetV1Resource()
          .setFullResourceName(SAMPLE_PROJECT_RESOURCE_1),
        new GoogleCloudAssetV1Resource()
          .setFullResourceName(SAMPLE_PROJECT_RESOURCE_2)))
      .setConditionEvaluation(new ConditionEvaluation()
        .setEvaluationValue("CONDITIONAL"));

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
          .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/folders/folder-1")
          .setAccessControlLists(List.of(
            parentFolderAcl,
            childFolderAndProjectAcl))
          .setIamBinding(new Binding()
            .setMembers(List.of("user:" + SAMPLE_USER))
            .setRole(SAMPLE_ROLE)
            .setCondition(new Expr().setExpression(JIT_CONDITION))))));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(SAMPLE_USER, SAMPLE_PROJECT_ID_1);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(2, roles.getItems().size());

    assertEquals(
      new ProjectRole(
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE),
        ProjectRole.Status.ELIGIBLE_FOR_JIT),
      roles.getItems().get(0));

    assertEquals(
      new ProjectRole(
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_2,
          SAMPLE_ROLE),
        ProjectRole.Status.ELIGIBLE_FOR_JIT),
      roles.getItems().get(1));
  }

  @Test
  public void whenStatusSetToJitOnly_ThenListEligibleProjectRolesOnlyReturnsJitEligibleBindings() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var jitEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      JIT_CONDITION,
      "eligible binding",
      "CONDITIONAL");

    var mpaEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE_2,
      SAMPLE_USER,
      MPA_CONDITION,
      "MPA-eligible binding",
      "CONDITIONAL");

    var activatedBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      "time ...",
      JitConstraints.ACTIVATION_CONDITION_TITLE,
      "TRUE");

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          jitEligibleBinding,
          mpaEligibleBinding,
          activatedBinding)));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(ProjectRole.Status.ELIGIBLE_FOR_JIT));

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(1, roles.getItems().size());

    var role = roles.getItems().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, role.getProjectId());
    assertEquals(ProjectRole.Status.ELIGIBLE_FOR_JIT, role.status);
  }

  @Test
  public void whenStatusSetToMpaOnly_ThenListEligibleProjectRolesOnlyReturnsMpaEligibleBindings() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var jitEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      JIT_CONDITION,
      "eligible binding",
      "CONDITIONAL");

    var mpaEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE_2,
      SAMPLE_USER,
      MPA_CONDITION,
      "MPA-eligible binding",
      "CONDITIONAL");

    var activatedBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      "time ...",
      JitConstraints.ACTIVATION_CONDITION_TITLE,
      "TRUE");

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          jitEligibleBinding,
          mpaEligibleBinding,
          activatedBinding)));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(ProjectRole.Status.ELIGIBLE_FOR_MPA));

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(1, roles.getItems().size());

    var role = roles.getItems().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, role.getProjectId());
    assertEquals(ProjectRole.Status.ELIGIBLE_FOR_MPA, role.status);
  }

  @Test
  public void whenStatusSetToActivatedOnly_ThenListEligibleProjectRolesOnlyReturnsActivatedBindings() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var jitEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      JIT_CONDITION,
      "eligible binding",
      "CONDITIONAL");

    var mpaEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE_2,
      SAMPLE_USER,
      MPA_CONDITION,
      "MPA-eligible binding",
      "CONDITIONAL");

    var activatedBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      "time ...",
      JitConstraints.ACTIVATION_CONDITION_TITLE,
      "TRUE");

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          jitEligibleBinding,
          mpaEligibleBinding,
          activatedBinding)));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var roles = service.listEligibleProjectRoles(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(ProjectRole.Status.ACTIVATED));

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getItems());
    assertEquals(1, roles.getItems().size());

    var role = roles.getItems().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, role.getProjectId());
    assertEquals(ProjectRole.Status.ACTIVATED, role.status);
  }

  // ---------------------------------------------------------------------
  // listEligibleUsersForProjectRole.
  // ---------------------------------------------------------------------

  @Test
  public void whenRoleIsNotEligible_ThenListEligibleUsersForProjectRoleThrowsException() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
            .setAttachedResourceFullName(SAMPLE_PROJECT_RESOURCE_1))));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    assertThrows(
      AccessDeniedException.class,
      () -> service.listEligibleUsersForProjectRole(
        SAMPLE_USER,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE)));
  }

  @Test
  public void whenCallerIsOnlyMpaEligibleUser_ThenListEligibleUsersForProjectRoleReturnsEmptyList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var mpaBindingResult = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      MPA_CONDITION,
      "eligible binding",
      "CONDITIONAL");
    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(mpaBindingResult)));
    when(assetAdapter.findPermissionedPrincipalsByResource(anyString(), anyString(), anyString()))
      .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(mpaBindingResult)));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var approvers = service.listEligibleUsersForProjectRole(
        SAMPLE_USER,
        new RoleBinding(
          SAMPLE_PROJECT_RESOURCE_1,
          SAMPLE_ROLE));

    assertTrue(approvers.isEmpty());
  }

  @Test
  public void whenMpaEligibleUsersIncludesOtherUser_ThenListEligibleUsersForProjectRoleReturnsList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var mpaBindingResult = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER,
      MPA_CONDITION,
      "eligible binding",
      "CONDITIONAL");
    var mpaBindingResultForOtherUser = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER_2,
      MPA_CONDITION,
      "eligible binding",
      "CONDITIONAL");
    var jitBindingResultForOtherUser = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_RESOURCE_1,
      SAMPLE_ROLE,
      SAMPLE_USER_3,
      JIT_CONDITION,
      "eligible binding",
      "CONDITIONAL");

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_RESOURCE_1)),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(mpaBindingResult)));
    when(assetAdapter.findPermissionedPrincipalsByResource(anyString(), anyString(), anyString()))
      .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(
        mpaBindingResult,
        mpaBindingResultForOtherUser,
        jitBindingResultForOtherUser)));

    var service = new RoleDiscoveryService(
      assetAdapter,
      new RoleDiscoveryService.Options("organizations/0"));

    var approvers = service.listEligibleUsersForProjectRole(
      SAMPLE_USER,
      new RoleBinding(
        SAMPLE_PROJECT_RESOURCE_1,
        SAMPLE_ROLE));

    assertEquals(1, approvers.size());
    assertEquals(SAMPLE_USER_2, approvers.stream().findFirst().get());
  }
}
