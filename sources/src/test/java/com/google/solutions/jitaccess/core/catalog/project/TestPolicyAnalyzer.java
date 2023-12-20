//
// Copyright 2023 Google LLC
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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.api.services.cloudasset.v1.model.*;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.clients.AssetInventoryClient;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class TestPolicyAnalyzer {
  private static final UserId SAMPLE_USER = new UserId("user-1", "user-1@example.com");
  private static final UserId SAMPLE_APPROVING_USER_1 = new UserId("approver-1", "approver-1@example.com");
  private static final UserId SAMPLE_APPROVING_USER_2 = new UserId("approver-2", "approver-2@example.com");
  private static final ProjectId SAMPLE_PROJECT_ID_1 = new ProjectId("project-1");
  private static final ProjectId SAMPLE_PROJECT_ID_2 = new ProjectId("project-2");
  private static final String SAMPLE_ROLE_1 = "roles/resourcemanager.role1";
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
  // findProjectsWithEntitlements.
  // ---------------------------------------------------------------------

  @Test
  public void whenAnalysisResultEmpty_ThenFindProjectsWithEntitlementsReturnsEmptyList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.of("resourcemanager.projects.get")),
        eq(Optional.empty()),
        eq(true)))
      .thenReturn(new IamPolicyAnalysis());

    var analyzer = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var projectIds = analyzer.findProjectsWithEntitlements(SAMPLE_USER);
    assertNotNull(projectIds);
    assertEquals(0, projectIds.size());
  }

  @Test
  public void whenAnalysisResultContainsAcsWithUnrecognizedConditions_ThenFindProjectsWithEntitlementsReturnsEmptyList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);
    var resourceManagerAdapter = Mockito.mock(ResourceManagerClient.class);

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
            SAMPLE_PROJECT_ID_1.getFullResourceName(),
            SAMPLE_ROLE_1,
            SAMPLE_USER,
            "a==b",
            "unrecognized condition",
            "TRUE"))));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var projectIds = service.findProjectsWithEntitlements(SAMPLE_USER);
    assertNotNull(projectIds);
    assertEquals(0, projectIds.size());
  }

  @Test
  public void whenAnalysisContainsPermanentBinding_ThenFindProjectsWithEntitlementsReturnsProjectId()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);
    var resourceManagerAdapter = Mockito.mock(ResourceManagerClient.class);

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
            SAMPLE_PROJECT_ID_1.getFullResourceName(),
            SAMPLE_ROLE_1,
            SAMPLE_USER))));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var projectIds = service.findProjectsWithEntitlements(SAMPLE_USER);
    assertNotNull(projectIds);
    assertEquals(1, projectIds.size());
    assertTrue(projectIds.contains(SAMPLE_PROJECT_ID_1));
  }

  @Test
  public void whenAnalysisContainsEligibleBindings_ThenFindProjectsWithEntitlementsReturnsProjectIds()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);
    var resourceManagerAdapter = Mockito.mock(ResourceManagerClient.class);

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
            SAMPLE_PROJECT_ID_1.getFullResourceName(),
            SAMPLE_ROLE_1,
            SAMPLE_USER,
            JIT_CONDITION,
            "eligible binding",
            "CONDITIONAL"),
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_ID_2.getFullResourceName(),
            SAMPLE_ROLE_1,
            SAMPLE_USER,
            MPA_CONDITION,
            "eligible binding",
            "CONDITIONAL"))));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var projectIds = service.findProjectsWithEntitlements(SAMPLE_USER);
    assertNotNull(projectIds);
    assertEquals(2, projectIds.size());
    assertTrue(projectIds.contains(SAMPLE_PROJECT_ID_1));
    assertTrue(projectIds.contains(SAMPLE_PROJECT_ID_2));
  }

  // ---------------------------------------------------------------------
  // FindEntitlements.
  // ---------------------------------------------------------------------

  @Test
  public void whenAnalysisResultEmpty_ThenFindEntitlementsReturnsEmptyList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis());

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(0, entitlements.items().size());
  }

  @Test
  public void whenAnalysisResultContainsEmptyAcl_ThenFindEntitlementsReturnsEmptyList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          new IamPolicyAnalysisResult().setAttachedResourceFullName(SAMPLE_PROJECT_ID_1.getFullResourceName()))));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(0, entitlements.items().size());
  }

  @Test
  public void whenAnalysisContainsNoEligibleRoles_ThenFindEntitlementsReturnsEmptyList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createIamPolicyAnalysisResult(
            SAMPLE_PROJECT_ID_1.getFullResourceName(),
            SAMPLE_ROLE_1,
            SAMPLE_USER))));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(0, entitlements.items().size());
  }

  @Test
  public void whenAnalysisContainsJitEligibleBinding_ThenFindEntitlementsReturnsList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_ID_1.getFullResourceName(),
            SAMPLE_ROLE_1,
            SAMPLE_USER,
            JIT_CONDITION,
            "eligible binding",
            "CONDITIONAL"))));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(1, entitlements.items().size());

    var entitlement = entitlements.items().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, entitlement.id().projectId());
    assertEquals(SAMPLE_ROLE_1, entitlement.id().roleBinding().role());
    assertEquals(SAMPLE_ROLE_1, entitlement.name());
    assertEquals(ActivationType.JIT, entitlement.activationType());
    assertEquals(Entitlement.Status.AVAILABLE, entitlement.status());
  }

  @Test
  public void whenAnalysisContainsDuplicateJitEligibleBinding_ThenFindEntitlementsReturnsList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_ID_1.getFullResourceName(),
            SAMPLE_ROLE_1,
            SAMPLE_USER,
            JIT_CONDITION,
            "eligible binding #1",
            "CONDITIONAL"),
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_ID_1.getFullResourceName(),
            SAMPLE_ROLE_1,
            SAMPLE_USER,
            JIT_CONDITION,
            "eligible binding #2",
            "CONDITIONAL"))));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(1, entitlements.items().size());

    var entitlement = entitlements.items().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, entitlement.id().projectId());
    assertEquals(SAMPLE_ROLE_1, entitlement.id().roleBinding().role());
    assertEquals(SAMPLE_ROLE_1, entitlement.name());
    assertEquals(ActivationType.JIT, entitlement.activationType());
    assertEquals(Entitlement.Status.AVAILABLE, entitlement.status());
  }

  @Test
  public void whenAnalysisContainsMpaEligibleBinding_ThenFindEntitlementsReturnsList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_ID_1.getFullResourceName(),
            SAMPLE_ROLE_1,
            SAMPLE_USER,
            MPA_CONDITION,
            "eligible binding",
            "CONDITIONAL"))));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(1, entitlements.items().size());

    var entitlement = entitlements.items().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, entitlement.id().projectId());
    assertEquals(SAMPLE_ROLE_1, entitlement.id().roleBinding().role());
    assertEquals(SAMPLE_ROLE_1, entitlement.name());
    assertEquals(ActivationType.MPA, entitlement.activationType());
    assertEquals(Entitlement.Status.AVAILABLE, entitlement.status());
  }

  @Test
  public void whenAnalysisContainsDuplicateMpaEligibleBinding_ThenFindEntitlementsReturnsList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_ID_1.getFullResourceName(),
            SAMPLE_ROLE_1,
            SAMPLE_USER,
            MPA_CONDITION,
            "eligible binding # 1",
            "CONDITIONAL"),
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_ID_1.getFullResourceName(),
            SAMPLE_ROLE_1,
            SAMPLE_USER,
            MPA_CONDITION,
            "eligible binding # 2",
            "CONDITIONAL"))));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(1, entitlements.items().size());

    var entitlement = entitlements.items().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, entitlement.id().projectId());
    assertEquals(SAMPLE_ROLE_1, entitlement.id().roleBinding().role());
    assertEquals(SAMPLE_ROLE_1, entitlement.name());
    assertEquals(ActivationType.MPA, entitlement.activationType());
    assertEquals(Entitlement.Status.AVAILABLE, entitlement.status());
  }

  @Test
  public void whenAnalysisContainsMpaEligibleBindingAndJitEligibleBindingForDifferentRoles_ThenFindEntitlementsReturnsList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    var jitEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_1,
      SAMPLE_USER,
      JIT_CONDITION,
      "JIT-eligible binding",
      "CONDITIONAL");

    var mpaEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_2,
      SAMPLE_USER,
      MPA_CONDITION,
      "MPA-eligible binding",
      "CONDITIONAL");

    when(assetAdapter.findAccessibleResourcesByUser(
      anyString(),
      eq(SAMPLE_USER),
      eq(Optional.empty()),
      eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
      eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(jitEligibleBinding, mpaEligibleBinding)));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(2, entitlements.items().size());

    var entitlement = entitlements.items().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, entitlement.id().projectId());
    assertEquals(SAMPLE_ROLE_1, entitlement.id().roleBinding().role());
    assertEquals(ActivationType.JIT, entitlement.activationType());
    assertEquals(Entitlement.Status.AVAILABLE, entitlement.status());

    entitlement = entitlements.items().stream().skip(1).findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, entitlement.id().projectId());
    assertEquals(SAMPLE_ROLE_2, entitlement.id().roleBinding().role());
    assertEquals(ActivationType.MPA, entitlement.activationType());
    assertEquals(Entitlement.Status.AVAILABLE, entitlement.status());
  }

  @Test
  public void whenAnalysisContainsMpaEligibleBindingAndJitEligibleBindingForSameRole_ThenFindEntitlementsReturnsList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    var jitEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_1,
      SAMPLE_USER,
      JIT_CONDITION,
      "JIT-eligible binding",
      "CONDITIONAL");

    var mpaEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_1,
      SAMPLE_USER,
      MPA_CONDITION,
      "MPA-eligible binding",
      "CONDITIONAL");

    when(assetAdapter.findAccessibleResourcesByUser(
      anyString(),
      eq(SAMPLE_USER),
      eq(Optional.empty()),
      eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
      eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(jitEligibleBinding, mpaEligibleBinding)));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(1, entitlements.items().size());

    // Only the JIT-eligible binding is retained.
    var entitlement = entitlements.items().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, entitlement.id().projectId());
    assertEquals(SAMPLE_ROLE_1, entitlement.id().roleBinding().role());
    assertEquals(ActivationType.JIT, entitlement.activationType());
    assertEquals(Entitlement.Status.AVAILABLE, entitlement.status());
  }

  @Test
  public void whenAnalysisContainsActivatedBinding_ThenFindEntitlementsReturnsMergedList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    var eligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_1,
      SAMPLE_USER,
      JIT_CONDITION,
      "eligible binding",
      "CONDITIONAL");

    var activatedBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_1,
      SAMPLE_USER,
      "time ...",
      JitConstraints.ACTIVATION_CONDITION_TITLE,
      "TRUE");

    var activatedExpiredBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_1,
      SAMPLE_USER,
      "time ...",
      JitConstraints.ACTIVATION_CONDITION_TITLE,
      "FALSE");

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          eligibleBinding,
          activatedBinding,
          activatedExpiredBinding
        )));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(1, entitlements.items().size());

    var entitlement = entitlements.items().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, entitlement.id().projectId());
    assertEquals(SAMPLE_ROLE_1, entitlement.id().roleBinding().role());
    assertEquals(ActivationType.JIT, entitlement.activationType());
    assertEquals(Entitlement.Status.ACTIVE, entitlement.status());
  }

  @Test
  public void whenAnalysisContainsEligibleBindingWithExtraCondition_ThenBindingIsIgnored()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          createConditionalIamPolicyAnalysisResult(
            SAMPLE_PROJECT_ID_1.getFullResourceName(),
            SAMPLE_ROLE_1,
            SAMPLE_USER,
            JIT_CONDITION + " && resource.name=='Foo'",
            "eligible binding with extra junk",
            "CONDITIONAL"))));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(0, entitlements.items().size());
  }

  @Test
  public void whenAnalysisContainsInheritedEligibleBinding_ThenFindEntitlementsReturnsList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

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
          .setFullResourceName(SAMPLE_PROJECT_ID_1.getFullResourceName()),
        new GoogleCloudAssetV1Resource()
          .setFullResourceName(SAMPLE_PROJECT_ID_2.getFullResourceName())))
      .setConditionEvaluation(new ConditionEvaluation()
        .setEvaluationValue("CONDITIONAL"));

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
          .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/folders/folder-1")
          .setAccessControlLists(List.of(
            parentFolderAcl,
            childFolderAndProjectAcl))
          .setIamBinding(new Binding()
            .setMembers(List.of("user:" + SAMPLE_USER))
            .setRole(SAMPLE_ROLE_1)
            .setCondition(new Expr().setExpression(JIT_CONDITION))))));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.AVAILABLE, Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(2, entitlements.items().size());

    var first = entitlements.items().first();
    assertEquals(
      new RoleBinding(SAMPLE_PROJECT_ID_1, SAMPLE_ROLE_1),
      first.id().roleBinding());
    assertEquals(
      ActivationType.JIT,
      first.activationType());

    var second = entitlements.items().last();
    assertEquals(
      new RoleBinding(SAMPLE_PROJECT_ID_2, SAMPLE_ROLE_1),
      second.id().roleBinding());
    assertEquals(
      ActivationType.JIT,
      second.activationType());
  }

  @Test
  public void whenStatusSetToActiveOnly_ThenFindEntitlementsOnlyReturnsActivatedBindings() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    var jitEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_1,
      SAMPLE_USER,
      JIT_CONDITION,
      "eligible binding",
      "CONDITIONAL");

    var mpaEligibleBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_2,
      SAMPLE_USER,
      MPA_CONDITION,
      "MPA-eligible binding",
      "CONDITIONAL");

    var activatedBinding = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_1,
      SAMPLE_USER,
      "time ...",
      JitConstraints.ACTIVATION_CONDITION_TITLE,
      "TRUE");

    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis()
        .setAnalysisResults(List.of(
          jitEligibleBinding,
          mpaEligibleBinding,
          activatedBinding)));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var entitlements = service.findEntitlements(
      SAMPLE_USER,
      SAMPLE_PROJECT_ID_1,
      EnumSet.of(Entitlement.Status.ACTIVE));

    assertNotNull(entitlements.warnings());
    assertEquals(0, entitlements.warnings().size());

    assertNotNull(entitlements.items());
    assertEquals(1, entitlements.items().size());

    var entitlement = entitlements.items().stream().findFirst().get();
    assertEquals(SAMPLE_PROJECT_ID_1, entitlement.id().projectId());
    assertEquals(Entitlement.Status.ACTIVE, entitlement.status());
  }


  // ---------------------------------------------------------------------
  // findApproversForEntitlement.
  // ---------------------------------------------------------------------

  @Test
  public void whenAllUsersJitEligible_ThenFindApproversForEntitlementReturnsEmptyList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);

    var mpaBindingResult = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_1,
      SAMPLE_USER,
      JIT_CONDITION,
      "eligible binding",
      "CONDITIONAL");
    when(assetAdapter
      .findAccessibleResourcesByUser(
        anyString(),
        eq(SAMPLE_USER),
        eq(Optional.empty()),
        eq(Optional.of(SAMPLE_PROJECT_ID_1.getFullResourceName())),
        eq(false)))
      .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(mpaBindingResult)));
    when(assetAdapter.findPermissionedPrincipalsByResource(anyString(), anyString(), anyString()))
      .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(mpaBindingResult)));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var approvers = service.findApproversForEntitlement(
      new RoleBinding(
        SAMPLE_PROJECT_ID_1,
        SAMPLE_ROLE_1));

    assertTrue(approvers.isEmpty());
  }

  @Test
  public void whenUsersMpaEligible_ThenFindApproversForEntitlementReturnsList() throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryClient.class);
    var resourceManagerAdapter = Mockito.mock(ResourceManagerClient.class);

    var jitBindingResult = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_1,
      SAMPLE_USER,
      JIT_CONDITION,
      "eligible binding",
      "CONDITIONAL");
    var mpaBindingResult1 = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_1,
      SAMPLE_APPROVING_USER_1,
      MPA_CONDITION,
      "eligible binding",
      "CONDITIONAL");
    var mpaBindingResult2 = createConditionalIamPolicyAnalysisResult(
      SAMPLE_PROJECT_ID_1.getFullResourceName(),
      SAMPLE_ROLE_1,
      SAMPLE_APPROVING_USER_2,
      MPA_CONDITION,
      "eligible binding",
      "CONDITIONAL");

    when(assetAdapter.findPermissionedPrincipalsByResource(anyString(), anyString(), anyString()))
      .thenReturn(new IamPolicyAnalysis().setAnalysisResults(List.of(
        jitBindingResult,
        mpaBindingResult1,
        mpaBindingResult2)));

    var service = new PolicyAnalyzer(
      assetAdapter,
      new PolicyAnalyzer.Options("organizations/0"));

    var approvers = service.findApproversForEntitlement(
      new RoleBinding(
        SAMPLE_PROJECT_ID_1,
        SAMPLE_ROLE_1));

    assertEquals(2, approvers.size());
    assertIterableEquals(
      List.of(SAMPLE_APPROVING_USER_1, SAMPLE_APPROVING_USER_2),
      approvers);
  }
}
