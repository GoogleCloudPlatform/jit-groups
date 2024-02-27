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
import com.google.solutions.jitaccess.cel.TemporaryIamCondition;
import com.google.solutions.jitaccess.core.catalog.ExternalApproval;
import com.google.solutions.jitaccess.core.catalog.PeerApproval;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege;
import com.google.solutions.jitaccess.core.catalog.ReviewerPrivilege;
import com.google.solutions.jitaccess.core.catalog.SelfApproval;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege.Status;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Helper class for creating privileges and parsing IAM conditions.
 */
class PrivilegeFactory {
  /** Condition title for activated role bindings */
  public static final String ACTIVATION_CONDITION_TITLE = "JIT access activation";

  public static final String VALID_TOPIC_PATTERN = "(\\.([a-zA-Z](([a-zA-Z0-9-_])*[a-zA-Z0-9])?))?";

  /**
   * Condition that marks a role binding as eligible for self approver privilege
   */
  private static final Pattern SELF_APPROVER_CONDITION_PATTERN = Pattern
      .compile("^\\s*has\\(\\s*\\{\\s*\\}.jitaccessconstraint\\s*\\)\\s*$");

  /** Condition that marks a role binding as eligible for peer privilege */
  private static final Pattern PEER_CONDITION_PATTERN = Pattern
      .compile("\\s*has\\(\\s*\\{\\s*\\}.multipartyapprovalconstraint" + VALID_TOPIC_PATTERN + "\\s*\\)\\s*$");

  /** Condition that marks a role binding as eligible for requester privilege */
  private static final Pattern REQUESTER_CONDITION_PATTERN = Pattern
      .compile("\\s*has\\(\\s*\\{\\s*\\}.externalapprovalconstraint" + VALID_TOPIC_PATTERN + "\\s*\\)\\s*$");

  /** Condition that marks a role binding as eligible for reviewer privilege */
  private static final Pattern REVIEWER_CONDITION_PATTERN = Pattern
      .compile("\\s*has\\(\\s*\\{\\s*\\}.reviewerprivilege" + VALID_TOPIC_PATTERN + "\\s*\\)\\s*$");

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
        .toLowerCase();

    return pattern.matcher(expression).matches();
  }

  private static String getTopic(Expr iamCondition, Pattern pattern) {
    var expression = iamCondition
        .getExpression()
        .toLowerCase();

    var matcher = pattern.matcher(expression);
    if (matcher.find()) {
      if (matcher.groupCount() == 4) {
        return matcher.group(2) == null ? "" : matcher.group(2);
      }
    }

    return "";
  }

  /** Check if the IAM condition indicates an activated role binding */
  public static boolean isActivated(Expr iamCondition) {
    return iamCondition != null && ACTIVATION_CONDITION_TITLE.equals(iamCondition.getTitle())
        && TemporaryIamCondition.isTemporaryAccessCondition(iamCondition.getExpression());
  }

  public static Optional<RequesterPrivilege<ProjectRoleBinding>> createRequesterPrivilege(
      ProjectRoleBinding projectRoleBinding,
      Expr iamCondition) {
    if (isMatchingCondition(iamCondition, SELF_APPROVER_CONDITION_PATTERN)) {
      return Optional
          .of(new RequesterPrivilege<ProjectRoleBinding>(projectRoleBinding,
              projectRoleBinding.roleBinding().role(),
              new SelfApproval(),
              Status.INACTIVE));
    } else if (isMatchingCondition(iamCondition, PEER_CONDITION_PATTERN)) {
      String topic = getTopic(iamCondition, PEER_CONDITION_PATTERN);
      return Optional
          .of(new RequesterPrivilege<ProjectRoleBinding>(projectRoleBinding,
              projectRoleBinding.roleBinding().role(), new PeerApproval(topic),
              Status.INACTIVE));
    } else if (isMatchingCondition(iamCondition, REQUESTER_CONDITION_PATTERN)) {
      String topic = getTopic(iamCondition, REQUESTER_CONDITION_PATTERN);
      return Optional
          .of(new RequesterPrivilege<ProjectRoleBinding>(projectRoleBinding,
              projectRoleBinding.roleBinding().role(), new ExternalApproval(topic),
              Status.INACTIVE));
    } else {
      return Optional.empty();
    }
  }

  public static Optional<ReviewerPrivilege<ProjectRoleBinding>> createReviewerPrivilege(
      ProjectRoleBinding projectRoleBinding,
      Expr iamCondition) {
    if (isMatchingCondition(iamCondition, PEER_CONDITION_PATTERN)) {
      String topic = getTopic(iamCondition, PEER_CONDITION_PATTERN);
      return Optional
          .of(new ReviewerPrivilege<ProjectRoleBinding>(projectRoleBinding,
              projectRoleBinding.roleBinding().role(),
              Set.of(new PeerApproval(topic))));
    }
    if (isMatchingCondition(iamCondition, REVIEWER_CONDITION_PATTERN)) {
      String topic = getTopic(iamCondition, REVIEWER_CONDITION_PATTERN);
      return Optional
          .of(new ReviewerPrivilege<ProjectRoleBinding>(projectRoleBinding,
              projectRoleBinding.roleBinding().role(),
              Set.of(new ExternalApproval(topic))));
    } else {
      return Optional.empty();
    }
  }
}
