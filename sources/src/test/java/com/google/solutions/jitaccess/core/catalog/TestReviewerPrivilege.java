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

package com.google.solutions.jitaccess.core.catalog;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestReviewerPrivilege {

    // -------------------------------------------------------------------------
    // toString.
    // -------------------------------------------------------------------------

    @Test
    public void toStringReturnsName() {
        var privilege = new ReviewerPrivilege<SamplePrivilegeId>(
                new SamplePrivilegeId("1"),
                "Sample privilege",
                EnumSet.of(ActivationType.SELF_APPROVAL));

        assertEquals("Sample privilege", privilege.toString());
    }

    // -------------------------------------------------------------------------
    // compareTo.
    // -------------------------------------------------------------------------

    @Test
    public void compareToOrdersByReviewableTypesThenName() {
        var selfApproval = new ReviewerPrivilege<SamplePrivilegeId>(
                new SamplePrivilegeId("A"),
                "Privilege A",
                EnumSet.of(ActivationType.SELF_APPROVAL));
        var peerApproval = new ReviewerPrivilege<SamplePrivilegeId>(
                new SamplePrivilegeId("A"),
                "Privilege A",
                EnumSet.of(ActivationType.PEER_APPROVAL));
        var noApproval = new ReviewerPrivilege<SamplePrivilegeId>(
                new SamplePrivilegeId("A"),
                "Privilege A",
                EnumSet.of(ActivationType.NONE));
        var externalApproval = new ReviewerPrivilege<SamplePrivilegeId>(
                new SamplePrivilegeId("A"),
                "Privilege A",
                EnumSet.of(ActivationType.EXTERNAL_APPROVAL));
        var allApprovals = new ReviewerPrivilege<SamplePrivilegeId>(
                new SamplePrivilegeId("A"),
                "Privilege A",
                EnumSet.of(ActivationType.EXTERNAL_APPROVAL, ActivationType.NONE, ActivationType.PEER_APPROVAL,
                        ActivationType.SELF_APPROVAL));
        var allApprovalsB = new ReviewerPrivilege<SamplePrivilegeId>(
                new SamplePrivilegeId("B"),
                "Privilege B",
                EnumSet.of(ActivationType.EXTERNAL_APPROVAL, ActivationType.NONE, ActivationType.PEER_APPROVAL,
                        ActivationType.SELF_APPROVAL));

        var privileges = List.of(
                selfApproval,
                peerApproval,
                noApproval,
                externalApproval,
                allApprovals,
                allApprovalsB);

        var sorted = new TreeSet<ReviewerPrivilege<SamplePrivilegeId>>();
        sorted.addAll(privileges);

        Assertions.assertIterableEquals(
                List.of(
                        externalApproval,
                        allApprovals,
                        allApprovalsB,
                        noApproval,
                        peerApproval,
                        selfApproval),
                sorted);
    }
}
