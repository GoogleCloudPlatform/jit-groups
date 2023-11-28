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

import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.solutions.jitaccess.core.data.MessageProperty;
import com.google.solutions.jitaccess.core.data.RoleBinding;
import com.google.solutions.jitaccess.core.data.UserId;
import com.google.solutions.jitaccess.core.data.ProjectId;
import com.google.solutions.jitaccess.core.adapters.PubSubAdapter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;

public class TestEventService {
  private static final UserId SAMPLE_USER = new UserId("user-1", "user-1@example.com");

  private static final ProjectId SAMPLE_PROJECT_ID = new ProjectId("project-1");
  private static final String SAMPLE_TOPIC_NAME_RAW = "projects/123/topics/sample-topic";

  private static final Binding SAMPLE_CONDITIONS = new Binding()
    .set("expression", "expression")
    .set("title", "Activated")
    .set("description", "description");
  private static final String SAMPLE_TOPIC_NAME = SAMPLE_TOPIC_NAME_RAW;

  private static final String NULL_TOPIC_NAME = null;
  private static final String SAMPLE_ROLE = "roles/mock.role1";

  private static final Instant CURR_TIME = Instant.now();
  private static final Instant END_TIME = CURR_TIME.plus(2, ChronoUnit.HOURS);

  private static final GenericJson conditions = new GenericJson()
    .set("expression", new GenericJson().set("start", CURR_TIME.atOffset(ZoneOffset.UTC).toString())
    .set("end", END_TIME.atOffset(ZoneOffset.UTC).toString()))
    .set("title", "JIT access activation")
    .set("description", "Approved by "+SAMPLE_USER.email+", justification: justification");

  private static final GenericJson payload = new GenericJson()
    .set("user", SAMPLE_USER.toString())
    .set("role", SAMPLE_ROLE)
    .set("project_id", SAMPLE_PROJECT_ID.id)
    .set("conditions", conditions);

  private static final MessageProperty SAMPLE_MESSAGE_PROPERTY = new MessageProperty(
    payload,
    MessageProperty.MessageOrigin.ROLE_ACTIVATED);

  private static final EventService.RoleActivatedEvent SAMPLE_BINDING_PUBSUB = new EventService.RoleActivatedEvent(
    SAMPLE_USER,
    new RoleBinding("//cloudresourcemanager.googleapis.com/projects/" + SAMPLE_PROJECT_ID.id, SAMPLE_ROLE),
    SAMPLE_PROJECT_ID,
    CURR_TIME,
    END_TIME,
    "justification");

  @Test
  public void whenTopicNameNull_ThenDoNothing() throws Exception{
    var adapter = Mockito.mock(PubSubAdapter.class);

    var service = new EventService.PubSubEventService(
      adapter,
      new EventService.PubSubEventService.Options(""));
    service.publish(SAMPLE_BINDING_PUBSUB);

    Mockito
      .verify(adapter, times(0))
      .publish(NULL_TOPIC_NAME, SAMPLE_MESSAGE_PROPERTY);
  }

  @Test
  public void whenValidTopicName_ThenPublishSucceeds() throws Exception{
    var adapter = Mockito.mock(PubSubAdapter.class);

    var service = new EventService.PubSubEventService(
      adapter,
      new EventService.PubSubEventService.Options(SAMPLE_TOPIC_NAME_RAW));
    service.publish(SAMPLE_BINDING_PUBSUB);

    Mockito
      .verify(adapter, times(1))
      .publish(SAMPLE_TOPIC_NAME, SAMPLE_MESSAGE_PROPERTY);
  }
}
