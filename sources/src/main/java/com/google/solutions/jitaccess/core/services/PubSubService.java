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

package com.google.solutions.jitaccess.core.services;

import com.google.api.client.json.GenericJson;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.adapters.PubSubAdapter;
import com.google.solutions.jitaccess.core.data.MessageProperty;
import com.google.solutions.jitaccess.core.data.ProjectId;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserId;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@ApplicationScoped
public abstract class PubSubService {
    public abstract void publishMessage(PubSubMessageBase pubSubMsg) throws PubSubException;


    // -------------------------------------------------------------------------
    // Inner classes.
    // -------------------------------------------------------------------------

    public static class GCPPubSubService extends PubSubService {
        private final PubSubAdapter pubSubAdapter;

        private final Options options;
        public GCPPubSubService(
                PubSubAdapter pubSubAdapter,
                Options options) {
            Preconditions.checkNotNull(pubSubAdapter, "pubSubAdapter");
            Preconditions.checkNotNull(options, "options");

            this.pubSubAdapter = pubSubAdapter;
            this.options = options;

        }

        @Override
        public void publishMessage(PubSubMessageBase pubSubMsg) throws PubSubException {
            if (this.getOptions().topicName == null) {
                return;
            }
            try {
                MessageProperty messageProperty = pubSubMsg.getMessageProperty();
                this.pubSubAdapter.publish(options.topicName, messageProperty);
            } catch (IOException| ExecutionException e){
                throw new PubSubException("pubsub publish exception", e);
            }

            
        }

        public Options getOptions() {
            return options;
        }

        public static class Options {
            /**
             * GCP PubSub TopicName
             * projects/{project}/topics/{topic}
             */
            public final String topicName;

            /**
             * Search inherited IAM policies
             */
            public Options(String topicNameRawStr) {
                this.topicName = topicNameRawStr;
            }
        }

    }

    public static class SilentPubSubService extends PubSubService {

        @Override
        public void publishMessage(PubSubMessageBase pubSubMsg) throws PubSubException {

        }
    }

    public static abstract class PubSubMessageBase {
        public UserId userId;
        public RoleBinding roleBinding;
        public String projectID;

        public PubSubMessageBase(UserId userId, RoleBinding roleBinding, String projectID) {
            this.userId = userId;
            this.roleBinding = roleBinding;
            this.projectID =projectID;
        }

        public abstract MessageProperty getMessageProperty();

    }

    public static class BindingPubSubMessage extends PubSubMessageBase {
        private Instant startTime;
        private Instant endTime;

        private String title = JitConstraints.ACTIVATION_CONDITION_TITLE;
        private String description;
        private String justification;

        public BindingPubSubMessage(UserId userId, RoleBinding roleBinding, String projectID, Instant startTime, Instant endTime, String justification){
            super(userId, roleBinding, projectID);

            this.startTime = startTime;
            this.endTime = endTime;
            this.justification = justification;

            this.description = String.format("Approved by %s, justification: %s", userId.toString(), this.justification);

        }

        public MessageProperty getMessageProperty(){
            var conditions = new GenericJson()
                    .set("expression", new GenericJson().set("start", this.startTime.atOffset(ZoneOffset.UTC).toString())
                            .set("end", this.endTime.atOffset(ZoneOffset.UTC).toString()))
                    .set("title", this.title)
                    .set("description", this.description);

            var payload = new GenericJson()
                    .set("user", this.userId.toString())
                    .set("role", this.roleBinding.role)
                    .set("project_id", ProjectId.fromFullResourceName(this.roleBinding.fullResourceName).id)
                    .set("conditions", conditions);
            return new MessageProperty(payload, MessageProperty.MessageOrigin.BINDING);
        }
    }

    public static class ApprovalPubSubMessage extends PubSubMessageBase {
        private Instant expiryTime;
        private List<String> peers;
        private int activationTimeout;
        private String activationRequestUrl;

        private String justification;

        private String title = "JIT approval request";
        private String description;

        public ApprovalPubSubMessage(UserId userId, RoleBinding roleBinding, String projectID, Instant expiryTime, String activationRequestUrl, String justification, int activationTimeout, List<String> peers){
            super(userId, roleBinding, projectID);

            this.expiryTime = expiryTime;
            this.activationRequestUrl = activationRequestUrl;

            this.activationTimeout = activationTimeout;
            this.peers = peers;
            this.justification = justification;

            this.description = String.format("Approved by %s, justification: %s", userId.toString(), this.justification);

        }

        public MessageProperty getMessageProperty(){
            var approvals = new GenericJson()
                    .set("activationExpiry", this.expiryTime.toString())
                    .set("activationUrl", this.activationRequestUrl.toString())
                    .set("duration", Duration.ofMinutes(this.activationTimeout).toString())
                    .set("requestPeers", this.peers.stream().map(email -> new UserId(email)).collect(Collectors.toSet()));

            var conditions = new GenericJson()
                    .set("description", String.format("Requesting approval, justification: %s", this.justification))
                    .set("title", "JIT approval request");

            var payload = new GenericJson()
                    .set("user", this.userId.toString())
                    .set("role", this.roleBinding.role)
                    .set("project_id", ProjectId.fromFullResourceName(this.roleBinding.fullResourceName).id)
                    .set("conditions", conditions)
                    .set("approvals", approvals);

            return new MessageProperty(payload,MessageProperty.MessageOrigin.APPROVAL);
        }
    }
    public static class ErrorPubSubMessage extends PubSubMessageBase {
        public ErrorPubSubMessage(UserId userId, RoleBinding roleBinding, String projectID){
            super(userId, roleBinding, projectID);

        }

        public MessageProperty getMessageProperty(){
            var payload = new GenericJson().set("user", this.userId.toString())
                    .set("role", this.roleBinding.role)
                    .set("project_id", this.projectID);

            return new MessageProperty(
                    payload,
                    MessageProperty.MessageOrigin.ERROR);
        }
    }
    public static class PubSubException extends Exception {
        public PubSubException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}


