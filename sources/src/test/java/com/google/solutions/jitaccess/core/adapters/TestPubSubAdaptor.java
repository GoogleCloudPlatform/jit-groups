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

package com.google.solutions.jitaccess.core.adapters;

import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.client.json.GenericJson;
import com.google.solutions.jitaccess.core.data.MessageProperty;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class TestPubSubAdaptor {


    @Test
    public void whenUnauthenticated_ThenThrowsAccessDeniedException() {

        var expression = new GenericJson().set("start", "11am")
                .set("end", "12pm");
        var bindingDescription = String.format(
                "Self-approved, justification: %s",
                "test justification");
        var condition = new GenericJson().set("expression", expression)
                .set("title", "Activated").set("description", bindingDescription);


        var binding = new Binding().setRole("project/viewer")
                .set("project_id", "project1")
                .setMembers(List.of("example@example.com"))
                .set("conditions", condition);


        var adapter = new PubSubAdaptor(IntegrationTestEnvironment.INVALID_CREDENTIAL);

        assertThrows(
                ExecutionException.class,
                () -> adapter.publish(
                        IntegrationTestEnvironment.TOPIC_NAME,
                        new MessageProperty(
                                binding,
                                MessageProperty.MessageOrigin.APPROVAL)));
    }

    @Test
    public void whenAuthenticated_ThenPublishMessage() throws InterruptedException, IOException, ExecutionException {
        // if project id configured but no topic name, just skip the test
        if (IntegrationTestEnvironment.PROJECT_ID != null && IntegrationTestEnvironment.TOPIC_NAME == null) {
            return;
        }

        var expression = new GenericJson().set("start", "11am")
                .set("end", "12pm");
        var bindingDescription = String.format(
                "Self-approved, justification: %s",
                "test justification");
        var condition = new GenericJson().set("expression", expression)
                .set("title", "Activated").set("description", bindingDescription);


        var binding = new Binding().setRole("project/viewer")
                .set("project_id", "project1")
                .setMembers(List.of("example@example.com"))
                .set("conditions", condition);

        var adapter = new PubSubAdaptor(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

        assertTrue(
                adapter.publish(
                        IntegrationTestEnvironment.TOPIC_NAME, // makesure the topic is exists
                        new MessageProperty(
                                binding,
                                MessageProperty.MessageOrigin.APPROVAL)).matches("^[0-9]*$"));


    }

}
