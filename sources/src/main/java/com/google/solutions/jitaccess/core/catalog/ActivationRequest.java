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

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.UserId;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/**
 * Represents a request for activating one or more requester privileges.
 */
public class ActivationRequest<TPrivilegeId extends PrivilegeId> {
    private final ActivationId id;
    private final Instant startTime;
    private final Duration duration;
    private final UserId requestingUser;
    private final TPrivilegeId requesterPrivilege;
    private final ActivationType activationType;
    private final String justification;
    private final Collection<UserId> reviewers;

    public ActivationRequest(
            ActivationId id,
            UserId requestingUser,
            Collection<UserId> reviewers,
            TPrivilegeId requesterPrivilege,
            ActivationType activationType,
            String justification,
            Instant startTime,
            Duration duration) {

        Preconditions.checkNotNull(id, "id");
        Preconditions.checkNotNull(requestingUser, "user");
        Preconditions.checkNotNull(requesterPrivilege, "requesterPrivilege");
        Preconditions.checkNotNull(reviewers, "reviewers");
        Preconditions.checkNotNull(justification, "justification");
        Preconditions.checkNotNull(startTime);
        Preconditions.checkNotNull(activationType);
        Preconditions.checkNotNull(duration);

        Preconditions.checkArgument(
                !reviewers.isEmpty() || activationType == ActivationType.SELF_APPROVAL,
                "At least one reviewer must be specified");

        Preconditions.checkArgument(
                !duration.isZero() && !duration.isNegative(),
                "The duration must be positive");

        Preconditions.checkArgument(
                activationType != ActivationType.NONE,
                "Activation type cannot be none");

        this.id = id;
        this.startTime = startTime;
        this.duration = duration;
        this.requestingUser = requestingUser;
        this.reviewers = reviewers;
        this.requesterPrivilege = requesterPrivilege;
        this.activationType = activationType;
        this.justification = justification;
    }

    /**
     * @return unique ID of the request.
     */
    public ActivationId id() {
        return this.id;
    }

    /**
     * @return start time for requested access.
     */
    public Instant startTime() {
        return this.startTime;
    }

    /**
     * @return duration of requested activation.
     */
    public Duration duration() {
        return this.duration;
    }

    /**
     * @return end time for requested access.
     */
    public Instant endTime() {
        return this.startTime.plus(this.duration);
    }

    /**
     * @return user that requested access.
     */
    public UserId requestingUser() {
        return this.requestingUser;
    }

    /**
     * @return users that can review request.
     */
    public Collection<UserId> reviewers() {
        return this.reviewers;
    }

    /**
     * @return requester privilege to activate.
     */
    public TPrivilegeId requesterPrivilege() {
        return this.requesterPrivilege;
    }

    /**
     * @return type of activation.
     */
    public ActivationType activationType() {
        return this.activationType;
    }

    /**
     * @return user-provided justification for the request.
     */
    public String justification() {
        return this.justification;
    }

    @Override
    public String toString() {
        return String.format(
                "[%s] requesterPrivilege=%s, startTime=%s, duration=%s, justification=%s",
                this.id,
                this.requesterPrivilege.toString(),
                this.startTime,
                this.duration,
                this.justification);
    }
}
