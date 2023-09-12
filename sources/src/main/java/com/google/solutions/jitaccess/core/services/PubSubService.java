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

import com.google.common.base.Preconditions;
import com.google.pubsub.v1.TopicName;
import com.google.solutions.jitaccess.core.adapters.PubSubAdaptor;
import com.google.solutions.jitaccess.core.data.MessageProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class PubSubService {

    private final PubSubAdaptor pubSubAdaptor;

    private final Options options;

    public PubSubService(
            PubSubAdaptor pubSubAdaptor,
            Options options) {
        Preconditions.checkNotNull(pubSubAdaptor, "pubSubAdaptor");
        Preconditions.checkNotNull(options, "options");

        this.pubSubAdaptor = pubSubAdaptor;
        this.options = options;

    }

    @Produces(MediaType.TEXT_PLAIN)
    public void publishMessage(MessageProperty messageProperty) throws InterruptedException, IOException, ExecutionException {
        if (this.getOptions().topicName != null) {
            this.pubSubAdaptor.publish(options.topicName, messageProperty);
            // add log not send and sent
        }

    }

    // -------------------------------------------------------------------------
    // Inner classes.
    // -------------------------------------------------------------------------

    public Options getOptions() {
        return options;
    }

    public static class Options {
        /**
         * GCP PubSub TopicName
         * projects/{project}/topics/{topic}
         */
        public final TopicName topicName;

        /**
         * Search inherited IAM policies
         */
        public Options(String topicNameRawStr) {
            this.topicName = TopicName.parse(topicNameRawStr);
        }
    }

}
