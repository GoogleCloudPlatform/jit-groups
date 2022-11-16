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
import com.google.solutions.jitaccess.core.adapters.AssetInventoryAdapter;
import com.google.solutions.jitaccess.core.adapters.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

public class TestRoleDiscoveryService {
  private static final UserId SAMPLE_USER = new UserId("user-1", "user-1@example.com");
  private static final String SAMPLE_ROLE = "roles/resourcemanager.projectIamAdmin";
  private static final String ELIGIBILITY_CONDITION = "has({}.jitAccessConstraint)";

  // ---------------------------------------------------------------------
  // listEligibleRoleBindings.
  // ---------------------------------------------------------------------

  @Test
  public void whenAnalysisResultEmpty_ThenListEligibleRoleBindingsReturnsEmptyList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.analyzeResourcesAccessibleByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(new IamPolicyAnalysis());

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(0, roles.getRoleBindings().size());
  }

  @Test
  public void whenAnalysisResultContainsEmptyAcl_ThenListEligibleRoleBindingsReturnsEmptyList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.analyzeResourcesAccessibleByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
            .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/projects/project-1"))));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(0, roles.getRoleBindings().size());
  }

  @Test
  public void whenAnalysisContainsNoEligibleRoles_ThenListEligibleRoleBindingsReturnsEmptyList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.analyzeResourcesAccessibleByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
            .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/projects/project-1")
            .setAccessControlLists(List.of(new GoogleCloudAssetV1AccessControlList()
              .setResources(List.of(new GoogleCloudAssetV1Resource()
                .setFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-1")))
              .setConditionEvaluation(new ConditionEvaluation()
                .setEvaluationValue("CONDITIONAL"))))
            .setIamBinding(new Binding()
              .setMembers(List.of("user:" + SAMPLE_USER))))));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(0, roles.getRoleBindings().size());
  }

  @Test
  public void whenAnalysisContainsEligibleBinding_ThenListEligibleRoleBindingsReturnsList()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.analyzeResourcesAccessibleByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
            .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/projects/project-1")
            .setAccessControlLists(List.of(new GoogleCloudAssetV1AccessControlList()
              .setResources(List.of(new GoogleCloudAssetV1Resource()
                .setFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-1")))
              .setConditionEvaluation(new ConditionEvaluation()
                .setEvaluationValue("CONDITIONAL"))))
            .setIamBinding(new Binding()
              .setMembers(List.of("user:" + SAMPLE_USER))
              .setRole(SAMPLE_ROLE)
              .setCondition(new Expr().setExpression(ELIGIBILITY_CONDITION))))));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(1, roles.getRoleBindings().size());

    var roleBinding = roles.getRoleBindings().stream().findFirst().get();
    assertEquals("project-1", roleBinding.getResourceName());
    assertEquals("roles/resourcemanager.projectIamAdmin", roleBinding.getRole());
    assertEquals(RoleBinding.RoleBindingStatus.ELIGIBLE, roleBinding.getStatus());
  }

  @Test
  public void whenAnalysisContainsActivatedBinding_ThenListEligibleRoleBindingsReturnsMergedList()
    throws Exception {

    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    var eligibleBinding = new IamPolicyAnalysisResult()
      .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/projects/project-1")
      .setAccessControlLists(List.of(new GoogleCloudAssetV1AccessControlList()
        .setResources(List.of(new GoogleCloudAssetV1Resource()
          .setFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-1")))
        .setConditionEvaluation(new ConditionEvaluation()
          .setEvaluationValue("CONDITIONAL"))))
      .setIamBinding(new Binding()
        .setMembers(List.of("user:" + SAMPLE_USER))
        .setRole(SAMPLE_ROLE)
        .setCondition(new Expr().setExpression(ELIGIBILITY_CONDITION)));

    var activatedBinding = new IamPolicyAnalysisResult()
      .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/projects/project-1")
      .setAccessControlLists(List.of(new GoogleCloudAssetV1AccessControlList()
        .setResources(List.of(new GoogleCloudAssetV1Resource()
          .setFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-1")))
        .setConditionEvaluation(new ConditionEvaluation()
          .setEvaluationValue("TRUE"))))
      .setIamBinding(new Binding()
        .setMembers(List.of("user:" + SAMPLE_USER))
        .setRole(SAMPLE_ROLE)
        .setCondition(new Expr()
          .setTitle(JitConstraints.ELEVATION_CONDITION_TITLE)
          .setExpression("time ...")));

    var activatedExpiredBinding = new IamPolicyAnalysisResult()
      .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/projects/project-1")
      .setAccessControlLists(List.of(new GoogleCloudAssetV1AccessControlList()
        .setResources(List.of(new GoogleCloudAssetV1Resource()
          .setFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-1")))
        .setConditionEvaluation(new ConditionEvaluation()
          .setEvaluationValue("FALSE"))))
      .setIamBinding(new Binding()
        .setMembers(List.of("user:" + SAMPLE_USER))
        .setRole(SAMPLE_ROLE)
        .setCondition(new Expr()
          .setTitle(JitConstraints.ELEVATION_CONDITION_TITLE)
          .setExpression("time ...")));

    when(assetAdapter.analyzeResourcesAccessibleByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(
            eligibleBinding,
            activatedBinding,
            activatedExpiredBinding
          )));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(1, roles.getRoleBindings().size());

    var roleBinding = roles.getRoleBindings().stream().findFirst().get();
    assertEquals("project-1", roleBinding.getResourceName());
    assertEquals("roles/resourcemanager.projectIamAdmin", roleBinding.getRole());
    assertEquals(RoleBinding.RoleBindingStatus.ACTIVATED, roleBinding.getStatus());
  }

  @Test
  public void whenAnalysisContainsEligibleBindingWithExtraCondition_ThenBindingIsIgnored()
    throws Exception {
    var assetAdapter = Mockito.mock(AssetInventoryAdapter.class);

    when(assetAdapter.analyzeResourcesAccessibleByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
            .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/projects/project-1")
            .setAccessControlLists(List.of(new GoogleCloudAssetV1AccessControlList()
              .setResources(List.of(new GoogleCloudAssetV1Resource()
                .setFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-1")))
              .setConditionEvaluation(new ConditionEvaluation()
                .setEvaluationValue("CONDITIONAL"))))
            .setIamBinding(new Binding()
              .setMembers(List.of("user:" + SAMPLE_USER))
              .setRole(SAMPLE_ROLE)
              .setCondition(new Expr()
                .setExpression(ELIGIBILITY_CONDITION + " && resource.name=='Foo'"))))));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(0, roles.getRoleBindings().size());
  }

  @Test
  public void
  whenAnalysisContainsInheritedEligibleBinding_ThenListEligibleRoleBindingsAsyncReturnsList()
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
          .setFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-1"),
        new GoogleCloudAssetV1Resource()
          .setFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-2")))
      .setConditionEvaluation(new ConditionEvaluation()
        .setEvaluationValue("CONDITIONAL"));

    when(assetAdapter.analyzeResourcesAccessibleByUser(anyString(), eq(SAMPLE_USER), eq(true)))
      .thenReturn(
        new IamPolicyAnalysis()
          .setAnalysisResults(List.of(new IamPolicyAnalysisResult()
            .setAttachedResourceFullName("//cloudresourcemanager.googleapis.com/folders/folder-1")
            .setAccessControlLists(List.of(
              parentFolderAcl,
              childFolderAndProjectAcl))
            .setIamBinding(new Binding()
              .setMembers(List.of("user:" + SAMPLE_USER))
              .setRole(SAMPLE_ROLE)
              .setCondition(new Expr().setExpression(ELIGIBILITY_CONDITION))))));

    var service =
      new RoleDiscoveryService(
        assetAdapter,
        new RoleDiscoveryService.Options(
          "organizations/0",
          true));

    var roles = service.listEligibleRoleBindings(SAMPLE_USER);

    assertNotNull(roles.getWarnings());
    assertEquals(0, roles.getWarnings().size());

    assertNotNull(roles.getRoleBindings());
    assertEquals(2, roles.getRoleBindings().size());

    assertEquals(
      new RoleBinding(
        "project-1",
        "//cloudresourcemanager.googleapis.com/projects/project-1",
        SAMPLE_ROLE,
        RoleBinding.RoleBindingStatus.ELIGIBLE),
      roles.getRoleBindings().get(0));

    assertEquals(
      new RoleBinding(
        "project-2",
        "//cloudresourcemanager.googleapis.com/projects/project-2",
        SAMPLE_ROLE,
        RoleBinding.RoleBindingStatus.ELIGIBLE),
      roles.getRoleBindings().get(1));
  }
}
