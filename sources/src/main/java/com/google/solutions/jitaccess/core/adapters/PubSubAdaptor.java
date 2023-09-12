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

import com.google.api.core.ApiFuture;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import com.google.solutions.jitaccess.core.Exceptions;
import com.google.solutions.jitaccess.core.data.MessageProperty;
import com.google.solutions.jitaccess.web.LogEvents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


@ApplicationScoped
public class PubSubAdaptor {
    private final GoogleCredentials credentials;

    private LogAdapter logAdapter = new LogAdapter();

    public PubSubAdaptor(GoogleCredentials credentials) {
        Preconditions.checkNotNull(credentials, "credentials");
        this.credentials = credentials;

    }

    private Publisher createClient(TopicName topicName) throws IOException {
        try {
            if (this.credentials != null) {
                return Publisher.newBuilder(topicName).setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();
            }
            return Publisher.newBuilder(topicName).build();
        } catch (IOException e) {
            throw new IOException("Creating a PubSub Publisher client failed", e);
        }
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String publish(TopicName topicName, MessageProperty messageProperty) throws InterruptedException, IOException, ExecutionException {

        // Create a Pub/Sub publisher client.
        var publisher = createClient(topicName);

        try {
            // TODO: add message signature as attribute of message to verify authenticity

            // Create a Pub/Sub message.
            Map<String, String> messageAttribute = new HashMap<>() {{
                put("origin", messageProperty.origin.toString());
            }};

            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFrom(messageProperty.getData().getBytes()))
                    .putAllAttributes(messageAttribute).build();
            // Publish the message
            ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);
            String messageId = messageIdFuture.get();

            return messageId;

        } catch (InterruptedException | ExecutionException e) {
            logAdapter.newErrorEntry(LogEvents.PUBLISH_MESSAGE, String.format(
                    "Publish Message to Topic %s failed: %s", topicName,
                    Exceptions.getFullMessage(e))).write();
            throw new ExecutionException("Failed to publish message", e);
        } finally {
            publisher.shutdown();
            publisher.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
