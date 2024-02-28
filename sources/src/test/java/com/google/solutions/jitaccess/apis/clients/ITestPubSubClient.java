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

package com.google.solutions.jitaccess.apis.clients;

import com.google.api.services.pubsub.model.PubsubMessage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ITestPubSubClient {

  // -------------------------------------------------------------------------
  // publish.
  // -------------------------------------------------------------------------

  @Test
  public void publish_whenUnauthenticated_ThenThrowsException() {
    var adapter = new PubSubClient(
      ITestEnvironment.INVALID_CREDENTIAL,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.publish(
        new PubSubTopic(ITestEnvironment.PROJECT_ID.id(), "topic-1"),
        new PubsubMessage()));
  }

  @Test
  public void publish_whenCallerLacksPermission_ThenThrowsException() {
    var adapter = new PubSubClient(
      ITestEnvironment.NO_ACCESS_CREDENTIALS,
      HttpTransport.Options.DEFAULT);
    assertThrows(
      AccessDeniedException.class,
      () -> adapter.publish(
        new PubSubTopic(ITestEnvironment.PROJECT_ID.id(), "topic-1"),
        new PubsubMessage()));
  }

  @Test
  public void publish() throws Exception {
    // if project id configured but no topic name, just skip the test
    Assumptions.assumeTrue(ITestEnvironment.PROJECT_ID != null);
    Assumptions.assumeTrue(ITestEnvironment.PUBSUB_TOPIC != null);

    var adapter = new PubSubClient(
      ITestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    var messageId = adapter.publish(
      ITestEnvironment.PUBSUB_TOPIC,
      new PubsubMessage().encodeData("test".getBytes(StandardCharsets.UTF_8)));

    assertNotNull(messageId);
  }
}
