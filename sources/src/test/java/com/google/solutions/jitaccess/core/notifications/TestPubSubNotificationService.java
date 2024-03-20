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

package com.google.solutions.jitaccess.core.notifications;

import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.clients.PubSubClient;
import com.google.solutions.jitaccess.core.clients.PubSubTopic;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestPubSubNotificationService {

  // -------------------------------------------------------------------------
  // sendNotification.
  // -------------------------------------------------------------------------

  private class SampleNotification extends NotificationService.Notification {

    protected SampleNotification(
      Collection<UserId> toRecipients,
      Collection<UserId> ccRecipients,
      String subject) {
      super(toRecipients, ccRecipients, subject);

      this.properties.put("string", "this is a string");
      this.properties.put("instant", Instant.ofEpochSecond(0));
      this.properties.put(
        "user_list",
        List.of(new UserId("alice@example.com"), new UserId("bob@example.com")));
    }

    @Override
    public String getType() {
      return "SampleNotification";
    }
  }

  @Test
  public void sendNotificationPublishesToPubSub() throws Exception {
    var adapter = Mockito.mock(PubSubClient.class);
    var topic = new PubSubTopic("project-1", "topic-1");
    var service = new PubSubNotificationService(
      adapter,
      new PubSubNotificationService.Options(topic));

    service.sendNotification(
      new SampleNotification(
        List.of(new UserId("to@example.com")),
        List.of(new UserId("cc@example.com")),
        "subject"));

    var expectedMessage =
      "eyJ0eXBlIjoiU2FtcGxlTm90aWZpY2F0aW9uIiwiYXR0cmlidXRlcyI6eyJzdHJpbmciOiJ0aGlzIGlzIGEgc3Rya" +
        "W5nIiwidXNlcl9saXN0IjpbImFsaWNlQGV4YW1wbGUuY29tIiwiYm9iQGV4YW1wbGUuY29tIl0sImluc3RhbnQi" +
        "OiIxOTcwLTAxLTAxVDAwOjAwOjAwWiJ9fQ";

    verify(adapter, times(1)).publish(
      eq(topic),
      argThat(m -> m.getData().equals(expectedMessage)));
  }
}
