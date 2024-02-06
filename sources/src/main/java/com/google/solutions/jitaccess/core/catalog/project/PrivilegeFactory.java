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
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege;
import com.google.solutions.jitaccess.core.catalog.ReviewerPrivilege;
import com.google.solutions.jitaccess.core.catalog.PrivilegeId;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege.Status;

import java.util.EnumSet;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Helper class for creating privileges and parsing IAM conditions.
 */
class PrivilegeFactory {
    /** Condition title for activated role bindings */
    public static final String ACTIVATION_CONDITION_TITLE = "JIT access activation";

    /**
     * Condition that marks a role binding as eligible for self approver privilege
     */
    private static final Pattern SELF_APPROVER_CONDITION_PATTERN = Pattern
            .compile("^\\s*has\\(\\s*\\{\\s*\\}.jitaccessconstraint\\s*\\)\\s*$");

    /** Condition that marks a role binding as eligible for peer privilege */
    private static final Pattern PEER_CONDITION_PATTERN = Pattern
            .compile("^\\s*has\\(\\s*\\{\\s*\\}.multipartyapprovalconstraint\\s*\\)\\s*$");

    /** Condition that marks a role binding as eligible for requester privilege */
    private static final Pattern REQUESTER_CONDITION_PATTERN = Pattern
            .compile("^\\s*has\\(\\s*\\{\\s*\\}.externalapprovalconstraint\\s*\\)\\s*$");

    /** Condition that marks a role binding as eligible for reviewer privilege */
    private static final Pattern REVIEWER_CONDITION_PATTERN = Pattern
            .compile("^\\s*has\\(\\s*\\{\\s*\\}.reviewerprivilege\\s*\\)\\s*$");

    private PrivilegeFactory() {
    }

    private static boolean isMatchingCondition(Expr iamCondition, Pattern pattern) {
        if (iamCondition == null) {
            return false;
        }

        if (iamCondition.getExpression() == null) {
            return false;
        }

        // Strip all whitespace to simplify expression matching.
        var expression = iamCondition
                .getExpression()
                .toLowerCase()
                .replace(" ", "");

        return pattern.matcher(expression).matches();
    }

    /** Check if the IAM condition is a JIT Access constraint */
    private static boolean isSelfApproverCondition(Expr iamCondition) {
        return isMatchingCondition(iamCondition, SELF_APPROVER_CONDITION_PATTERN);
    }

    /** Check if the IAM condition is a peer approval constraint */
    private static boolean isPeerCondition(Expr iamCondition) {
        return isMatchingCondition(iamCondition, PEER_CONDITION_PATTERN);
    }

    /** Check if the IAM condition is an external approval constraint */
    private static boolean isRequesterCondition(Expr iamCondition) {
        return isMatchingCondition(iamCondition, REQUESTER_CONDITION_PATTERN);
    }

    /** Check if the IAM condition is a reviewer privilege */
    private static boolean isReviewerCondition(Expr iamCondition) {
        return isMatchingCondition(iamCondition, REVIEWER_CONDITION_PATTERN);
    }

    /** Check if the IAM condition indicates an activated role binding */
    public static boolean isActivated(Expr iamCondition) {
        return iamCondition != null && ACTIVATION_CONDITION_TITLE.equals(iamCondition.getTitle());
    }

    public static Optional<RequesterPrivilege<ProjectRoleBinding>> createRequesterPrivilege(
            ProjectRoleBinding projectRoleBinding,
            Expr iamCondition) {
        if (isSelfApproverCondition(iamCondition)) {
            return Optional
                    .of(new RequesterPrivilege<ProjectRoleBinding>(projectRoleBinding,
                            projectRoleBinding.roleBinding().role(),
                            ActivationType.SELF_APPROVAL,
                            Status.AVAILABLE));
        } else if (isPeerCondition(iamCondition)) {
            return Optional
                    .of(new RequesterPrivilege<ProjectRoleBinding>(projectRoleBinding,
                            projectRoleBinding.roleBinding().role(), ActivationType.PEER_APPROVAL,
                            Status.AVAILABLE));
        } else if (isRequesterCondition(iamCondition)) {
            return Optional
                    .of(new RequesterPrivilege<ProjectRoleBinding>(projectRoleBinding,
                            projectRoleBinding.roleBinding().role(), ActivationType.EXTERNAL_APPROVAL,
                            Status.AVAILABLE));
        } else {
            return Optional.empty();
        }
    }

    public static Optional<ReviewerPrivilege<ProjectRoleBinding>> createReviewerPrivilege(
            ProjectRoleBinding projectRoleBinding,
            Expr iamCondition) {
        if (isPeerCondition(iamCondition)) {
            return Optional
                    .of(new ReviewerPrivilege<ProjectRoleBinding>(projectRoleBinding,
                            projectRoleBinding.roleBinding().role(),
                            EnumSet.of(ActivationType.PEER_APPROVAL)));
        }
        if (isReviewerCondition(iamCondition)) {
            return Optional
                    .of(new ReviewerPrivilege<ProjectRoleBinding>(projectRoleBinding,
                            projectRoleBinding.roleBinding().role(),
                            EnumSet.of(ActivationType.EXTERNAL_APPROVAL)));
        } else {
            return Optional.empty();
        }
    }
}
