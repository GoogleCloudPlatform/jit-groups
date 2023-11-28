////
//// Copyright 2023 Google LLC
////
//// Licensed to the Apache Software Foundation (ASF) under one
//// or more contributor license agreements.  See the NOTICE file
//// distributed with this work for additional information
//// regarding copyright ownership.  The ASF licenses this file
//// to you under the Apache License, Version 2.0 (the
//// "License"); you may not use this file except in compliance
//// with the License.  You may obtain a copy of the License at
////
////   http://www.apache.org/licenses/LICENSE-2.0
////
//// Unless required by applicable law or agreed to in writing,
//// software distributed under the License is distributed on an
//// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//// KIND, either express or implied.  See the License for the
//// specific language governing permissions and limitations
//// under the License.
////
//
//package com.google.solutions.jitaccess.core.services;
//
//import com.google.api.client.json.GenericJson;
//import com.google.api.services.pubsub.model.PubsubMessage;
//import com.google.common.base.Preconditions;
//import com.google.solutions.jitaccess.core.AccessException;
//import com.google.solutions.jitaccess.core.adapters.PubSubAdapter;
//import com.google.solutions.jitaccess.core.data.MessageProperty;
//import com.google.solutions.jitaccess.core.data.ProjectId;
//import com.google.solutions.jitaccess.core.data.RoleBinding;
//import com.google.solutions.jitaccess.core.data.UserId;
//import jakarta.enterprise.context.ApplicationScoped;
//
//import java.io.IOException;
//import java.time.Duration;
//import java.time.Instant;
//import java.time.ZoneOffset;
//import java.util.HashMap;
//import java.util.List;
//import java.util.stream.Collectors;
//
///**
// * Service for notifying other applications about activation requests and
// * related events.
// */
//@ApplicationScoped
//public abstract class EventService {
//  /**
//   * Publish an event.
//   */
//  public abstract void publish(EventBase event) throws EventException;
//
//  // -------------------------------------------------------------------------
//  // Implementation classes.
//  // -------------------------------------------------------------------------
//
//  public static class PubSubEventService extends EventService {
//    private final PubSubAdapter pubSubAdapter
//    private final Options options;
//
//    public PubSubEventService(
//      PubSubAdapter adapter,
//      Options options
//    ) {
//      Preconditions.checkNotNull(adapter, "adapter");
//      Preconditions.checkNotNull(options, "options");
//      Preconditions.checkNotNull(options.topicResourceName, "options");
//
//      this.pubSubAdapter = adapter;
//      this.options = options;
//    }
//
//    @Override
//    public void publish(EventBase event) throws EventException {
//      try {
//        var messageProperty = event.getMessageProperty();
//
//        var message = new PubsubMessage()
//          .encodeData(messageProperty.getData().getBytes("UTF-8"));
//
//        var messageAttributes = new HashMap<String, String>();
//        messageAttributes.put("origin", messageProperty.origin.toString());
//        message.setAttributes(messageAttributes);
//
//        this.pubSubAdapter.publish(options.topicResourceName, message);
//      } catch (AccessException | IOException e){
//        throw new EventException("Publishing event to PubSub failed", e);
//      }
//    }
//
//    public Options getOptions() {
//      return options;
//    }
//
//    /**
//     * @param topicResourceName PubSub topic in format projects/{project}/topics/{topic}
//     */
//    public record Options(String topicResourceName) {
//    }
//  }
//
//  public static class SilentEventService extends EventService {
//    @Override
//    public void publish(EventBase pubSubMsg) {
//    }
//  }
//
//  // -------------------------------------------------------------------------
//  // Event classes.
//  // -------------------------------------------------------------------------
//
//  public static abstract class EventBase {
//    public final UserId userId;
//    public final RoleBinding roleBinding;
//    public final ProjectId projectId;
//
//    public EventBase(UserId userId, RoleBinding roleBinding, ProjectId projectId) {
//      this.userId = userId;
//      this.roleBinding = roleBinding;
//      this.projectId = projectId;
//    }
//
//    public abstract MessageProperty getMessageProperty();
//  }
//
//  public static class RoleActivatedEvent extends EventBase {
//    private final Instant startTime;
//    private final Instant endTime;
//    private final String description;
//
//    public RoleActivatedEvent(
//      UserId userId,
//      RoleBinding roleBinding,
//      ProjectId projectId,
//      Instant startTime,
//      Instant endTime,
//      String justification){
//      super(userId, roleBinding, projectId);
//
//      this.startTime = startTime;
//      this.endTime = endTime;
//      this.description = String.format(
//        "Approved by %s, justification: %s", userId.toString(),
//        justification);
//    }
//
//    public MessageProperty getMessageProperty(){
//      var conditions = new GenericJson()
//        .set("expression", new GenericJson()
//        .set("start", this.startTime.atOffset(ZoneOffset.UTC).toString())
//        .set("end", this.endTime.atOffset(ZoneOffset.UTC).toString()))
//        .set("title", JitConstraints.ACTIVATION_CONDITION_TITLE)
//        .set("description", this.description);
//
//      var payload = new GenericJson()
//        .set("user", this.userId.toString())
//        .set("role", this.roleBinding.role)
//        .set("project_id", this.projectId.id)
//        .set("conditions", conditions);
//      return new MessageProperty(payload, MessageProperty.MessageOrigin.ROLE_ACTIVATED);
//    }
//  }
//
//  public static class RequestApprovedEvent extends EventBase {
//    private final Instant expiryTime;
//    private final List<String> peers;
//    private final int activationTimeout;
//    private final String activationRequestUrl;
//    private final String justification;
//
//    public RequestApprovedEvent(
//      UserId userId,
//      RoleBinding roleBinding,
//      ProjectId projectId,
//      Instant expiryTime,
//      String activationRequestUrl,
//      String justification,
//      int activationTimeout,
//      List<String> peers
//    ) {
//      super(userId, roleBinding, projectId);
//
//      this.expiryTime = expiryTime;
//      this.activationRequestUrl = activationRequestUrl;
//      this.activationTimeout = activationTimeout;
//      this.peers = peers;
//      this.justification = justification;
//    }
//
//    public MessageProperty getMessageProperty(){
//      var approvals = new GenericJson()
//        .set("activationExpiry", this.expiryTime.toString())
//        .set("activationUrl", this.activationRequestUrl)
//        .set("duration", Duration.ofMinutes(this.activationTimeout).toString())
//        .set("requestPeers", this.peers
//          .stream()
//          .map(UserId::new)
//          .collect(Collectors.toSet()));
//
//      var conditions = new GenericJson()
//        .set("description", String.format("Requesting approval, justification: %s", this.justification))
//        .set("title", "JIT approval request");
//
//      var payload = new GenericJson()
//        .set("user", this.userId.toString())
//        .set("role", this.roleBinding.role)
//        .set("project_id", this.projectId.id)
//        .set("conditions", conditions)
//        .set("approvals", approvals);
//
//      return new MessageProperty(payload,MessageProperty.MessageOrigin.REQUEST_APPROVED);
//    }
//  }
//
//  public static class EventException extends Exception {
//    public EventException(String message, Throwable cause) {
//      super(message, cause);
//    }
//  }
//}
//
//
