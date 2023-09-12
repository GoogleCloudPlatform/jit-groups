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

import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.pubsub.v1.TopicName;
import com.google.solutions.jitaccess.core.adapters.PubSubAdaptor;
import com.google.solutions.jitaccess.core.data.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import static org.mockito.Mockito.*;


public class TestPubSubService {
    private static final UserId SAMPLE_USER = new UserId("user-1", "user-1@example.com");

    private static final ProjectId SAMPLE_PROJECT_ID = new ProjectId("project-1");
    private static final String SAMPLE_TOPIC_NAME_RAW = "projects/123/topics/sample-topic";

    private static final Binding SAMPLE_CONDITIONS = new Binding().set("expression", "expression")
            .set("title", "Activated")
            .set("description", "description");
    private static final TopicName SAMPLE_TOPIC_NAME = TopicName.parse(SAMPLE_TOPIC_NAME_RAW);

    private static final TopicName NULL_TOPIC_NAME = null;
    private static final String SAMPLE_ROLE = "roles/mock.role1";
//    private static final MessageProperty SAMPLE_MESSAGE_PROPERTY = new MessageProperty(
//            SAMPLE_USER.email,
//            SAMPLE_CONDITIONS,
//            SAMPLE_ROLE,
//            SAMPLE_PROJECT_ID.toString(),
//            MessageProperty.MessageOrigin.APPROVAL);



//    @Test
//    public void whenTopicNameNull_ThenDoNothing() throws Exception{
//        var pubsubAdapter = Mockito.mock(PubSubAdaptor.class);
//
//        // method will not be executed
//
//        var service = new PubSubService(
//                pubsubAdapter,
//                new PubSubService.Options(""));
//        service.publishMessage(SAMPLE_MESSAGE_PROPERTY);
//
//        Mockito.verify(pubsubAdapter, times(0)).publish(
//                NULL_TOPIC_NAME, SAMPLE_MESSAGE_PROPERTY);
//
//    }
//
//    @Test
//    public void whenValidTopicName_ThenPublishMessage() throws Exception{
//        var pubsubAdapter = Mockito.mock(PubSubAdaptor.class);
//
//        var service = new PubSubService(
//                pubsubAdapter,
//                new PubSubService.Options(SAMPLE_TOPIC_NAME_RAW));
//        service.publishMessage(SAMPLE_MESSAGE_PROPERTY);
//
//        Mockito.verify(pubsubAdapter, times(1)).publish(
//                SAMPLE_TOPIC_NAME, SAMPLE_MESSAGE_PROPERTY);
//
//    }

}
