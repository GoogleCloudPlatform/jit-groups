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

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;

import com.google.api.services.pubsub.model.PublishResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;


import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.PubsubMessage;
import com.google.api.services.pubsub.model.PublishRequest;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.ApplicationVersion;
import com.google.solutions.jitaccess.core.Exceptions;
import com.google.solutions.jitaccess.core.data.MessageProperty;
import com.google.solutions.jitaccess.web.LogEvents;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


@ApplicationScoped
public class PubSubAdapter {
    private final GoogleCredentials credentials;

    private LogAdapter logAdapter = new LogAdapter();

    public PubSubAdapter(GoogleCredentials credentials) {
        Preconditions.checkNotNull(credentials, "credentials");
        this.credentials = credentials;

    }

    private Pubsub createClient( ) throws IOException {
        try {

            return new Pubsub.Builder(
                    HttpTransport.newTransport(),
                    new GsonFactory(),
                    new HttpCredentialsAdapter(this.credentials)).setApplicationName(ApplicationVersion.USER_AGENT).build();
        }
        catch (GeneralSecurityException e) {
            throw new IOException("Creating a ResourceManager client failed", e);
        }
    }

    public String publish(String topicName, MessageProperty messageProperty) throws IOException, ExecutionException {

        // Create a Pub/Sub publisher client.
        var pubsub = createClient();

        // Publish the message
        try {
            // Create a Pub/Sub message.
            PubsubMessage pubsubMessage = new PubsubMessage().encodeData(messageProperty.getData().getBytes("UTF-8"));

            // Create a publish request
            PublishRequest publishRequest = new PublishRequest();
            publishRequest.setMessages(Arrays.asList(pubsubMessage));

            Map<String, String> messageAttribute = new HashMap<>() {{
                put("origin", messageProperty.origin.toString());
            }};
            pubsubMessage.setAttributes(messageAttribute);
            PublishResponse res = pubsub.projects().topics().publish(topicName, publishRequest).execute();
            if (res.getMessageIds().size() < 1){
                throw new ExecutionException("Failed to publish message", new Throwable("emtpy response"));
            }
            String msgID = res.getMessageIds().get(0);
            return msgID;

        } catch (ExecutionException | IllegalArgumentException |GoogleJsonResponseException e) {
            logAdapter.newErrorEntry(LogEvents.PUBLISH_MESSAGE, String.format(
                    "Publish Message to Topic %s failed: %s", topicName,
                    Exceptions.getFullMessage(e))).write();
            throw new ExecutionException("Failed to publish message", e);
        }
    }
}
