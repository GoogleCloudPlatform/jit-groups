//
// Copyright 2022 Google LLC
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

import com.google.api.services.cloudasset.v1.model.Expr;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.catalog.ExternalApproval;
import com.google.solutions.jitaccess.core.catalog.PeerApproval;
import com.google.solutions.jitaccess.core.catalog.SelfApproval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Collectors;

public class TestPrivilegeFactory {

    // ---------------------------------------------------------------------
    // createRequesterPrivilege
    // ---------------------------------------------------------------------

    @Test
    public void whenConditionIsEmpty_ThenCreateRequesterPrivilegeReturnsEmpty() {
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")),
                new Expr().setExpression(""));
        assertTrue(privilege.isEmpty());
    }

    @Test
    public void whenExpressionIsNull_ThenCreateRequesterPrivilegeReturnsFalse() {
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), null);
        assertTrue(privilege.isEmpty());
    }

    @Test
    public void whenSelfApprovalConditionHasRedundantWhitespace_ThenCreateRequesterPrivilegeReturnsSelfApprovalPrivilege() {
        var condition = new Expr()
                .setExpression(" \r\n\t has( {  }.jitAccessConstraint \t ) \t \r\n\r");
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertEquals(new SelfApproval().name(), privilege.get().activationType().name());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenSelfApprovalConditionUsesWrongCase_ThenCreateRequesterPrivilegeReturnsSelfApprovalPrivilege() {
        var condition = new Expr()
                .setExpression("HAS({}.JitacceSSConstraint)");
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertEquals(new SelfApproval().name(), privilege.get().activationType().name());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenPeerApprovalConditionHasRedundantWhitespace_ThenCreateRequesterPrivilegeReturnsPeerApprovalPrivilege() {
        var condition = new Expr()
                .setExpression(" \r\n\t has( { \t\n\r\n }.multipartyapprovalconstraint.topic \t ) \t \r\n\r");
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertEquals(new PeerApproval("topic").name(), privilege.get().activationType().name());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenPeerApprovalConditionUsesWrongCase_ThenCreateRequesterPrivilegeReturnsPeerApprovalPrivilege() {
        var condition = new Expr()
                .setExpression("HAS({}.MUltiPARtyapPRovalconsTRAInT.topic)");
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertEquals(new PeerApproval("topic").name(), privilege.get().activationType().name());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenPeerApprovalNoTopic_ThenCreateRequesterPrivilegeReturnsPeerApprovalPrivilege() {
        var condition = new Expr()
                .setExpression("has({}.multiPartyApprovalConstraint)");
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertEquals(new PeerApproval("").name(), privilege.get().activationType().name());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenPeerApprovalInvalidTopic_ThenCreateRequesterPrivilegeReturnsEmpty() {
        var condition = new Expr()
                .setExpression("has({}.multipartyapprovalconstraint.123)");
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isEmpty());
    }

    @Test
    public void whenExternalApprovalConditionHasRedundantWhitespace_ThenCreateRequesterPrivilegeReturnsExternalApprovalPrivilege() {
        var condition = new Expr()
                .setExpression(" \r\n\t has( { \t\n\r\n }.externalApprovalConstraint.topic \t ) \t \r\n\r");
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertEquals(new ExternalApproval("topic").name(), privilege.get().activationType().name());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenExternalApprovalConditionUsesWrongCase_ThenCreateRequesterPrivilegeReturnsExternalApprovalPrivilege() {
        var condition = new Expr()
                .setExpression("HAS({}.exTErNaLapproVALConstraint.topic)");
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertEquals(new ExternalApproval("topic").name(), privilege.get().activationType().name());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenExternalApprovalNoTopic_ThenCreateRequesterPrivilegeReturnsPeerApprovalPrivilege() {
        var condition = new Expr()
                .setExpression("has({}.externalApprovalConstraint)");
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertEquals(new ExternalApproval("").name(), privilege.get().activationType().name());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenExternalApprovalInvalidTopic_ThenCreateRequesterPrivilegeReturnsEmpty() {
        var condition = new Expr()
                .setExpression("has({}.externalApprovalConstraint.123)");
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isEmpty());
    }

    @Test
    public void whenReviewerCondition_ThenCreateRequesterPrivilegeReturnsEmpty() {
        var condition = new Expr()
                .setExpression("has({}.reviewerPrivilege)");
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isEmpty());
    }

    // ---------------------------------------------------------------------
    // createReviewerPrivilege
    // ---------------------------------------------------------------------

    @Test
    public void whenConditionIsEmpty_ThenCreateReviewerPrivilegeReturnsEmpty() {
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")),
                new Expr().setExpression(""));
        assertTrue(privilege.isEmpty());
    }

    @Test
    public void whenExpressionIsNull_ThenCreateReviewerPrivilegeReturnsFalse() {
        var privilege = PrivilegeFactory.createRequesterPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), null);
        assertTrue(privilege.isEmpty());
    }

    @Test
    public void whenSelfApprovalCondition_ThenCreateReviewerPrivilegeReturnsEmpty() {
        var condition = new Expr()
                .setExpression("has( {  }.jitAccessConstraint )");
        var privilege = PrivilegeFactory.createReviewerPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isEmpty());
    }

    @Test
    public void whenPeerApprovalConditionHasRedundantWhitespace_ThenCreateReviewerPrivilegeReturnsPeerReviewerPrivilege() {
        var condition = new Expr()
                .setExpression(" \r\n\t has( { \t\n\r\n }.multipartyapprovalconstraint.topic \t ) \t \r\n\r");
        var privilege = PrivilegeFactory.createReviewerPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertTrue(privilege.get().reviewableTypes().stream().map(type -> type.name()).collect(Collectors.toList())
                .contains(new PeerApproval("topic").name()));
        assertEquals(1, privilege.get().reviewableTypes().size());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenPeerApprovalConditionUsesWrongCase_ThenCreateReviewerPrivilegeReturnsPeerReviewerPrivilege() {
        var condition = new Expr()
                .setExpression("HAS({}.MUltiPARtyapPRovalconsTRAInT.topic)");
        var privilege = PrivilegeFactory.createReviewerPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertTrue(privilege.get().reviewableTypes().stream().map(type -> type.name()).collect(Collectors.toList())
                .contains(new PeerApproval("topic").name()));
        assertEquals(1, privilege.get().reviewableTypes().size());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenPeerApprovalNoTopic_ThenCreateReviewerPrivilegeReturnsPeerReviewerPrivilege() {
        var condition = new Expr()
                .setExpression("has({}.multiPartyApprovalConstraint)");
        var privilege = PrivilegeFactory.createReviewerPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertEquals(new PeerApproval("").name(), privilege.get().reviewableTypes().stream().findFirst().get().name());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenPeerApprovalInvalidTopic_ThenCreateReviewerPrivilegeReturnsEmpty() {
        var condition = new Expr()
                .setExpression("has({}.multiPartyApprovalContraint.123)");
        var privilege = PrivilegeFactory.createReviewerPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isEmpty());
    }

    @Test
    public void whenExternalApprovalCondition_ThenCreateReviewerPrivilegeReturnsEmpty() {
        var condition = new Expr()
                .setExpression("has( {}.externalApprovalConstraint )");
        var privilege = PrivilegeFactory.createReviewerPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isEmpty());
    }

    @Test
    public void whenReviewerConditionHasRedundantWhitespace_ThenCreateReviewerPrivilegeReturnsExternalReviewerPrivilege() {
        var condition = new Expr()
                .setExpression(" \r\n\t has( { \t\n\r\n }.reviewerPrivilege.topic \t ) \t \r\n\r");
        var privilege = PrivilegeFactory.createReviewerPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertTrue(privilege.get().reviewableTypes().stream().map(type -> type.name()).collect(Collectors.toList())
                .contains(new ExternalApproval("topic").name()));
        assertEquals(1, privilege.get().reviewableTypes().size());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenReviewerConditionUsesWrongCase_ThenCreateReviewerPrivilegeReturnsExternalReviewerPrivilege() {
        var condition = new Expr()
                .setExpression("HAS({}.reviewERPrivIlege.topic)");
        var privilege = PrivilegeFactory.createReviewerPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertTrue(privilege.get().reviewableTypes().stream().map(type -> type.name()).collect(Collectors.toList())
                .contains(new ExternalApproval("topic").name()));
        assertEquals(1, privilege.get().reviewableTypes().size());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenExternalApprovalNoTopic_ThenCreateReviewerPrivilegeReturnsExternalReviewerPrivilege() {
        var condition = new Expr()
                .setExpression("has({}.reviewerPrivilege)");
        var privilege = PrivilegeFactory.createReviewerPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isPresent());
        assertEquals(new ExternalApproval("").name(),
                privilege.get().reviewableTypes().stream().findFirst().get().name());
        assertEquals("role", privilege.get().name());
    }

    @Test
    public void whenExternalApprovalInvalidTopic_ThenCreateReviewerPrivilegeReturnsEmpty() {
        var condition = new Expr()
                .setExpression("has({}.reviewerPrivilege.123)");
        var privilege = PrivilegeFactory.createReviewerPrivilege(
                new ProjectRoleBinding(new RoleBinding(new ProjectId("project"), "role")), condition);
        assertTrue(privilege.isEmpty());
    }
}