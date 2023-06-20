package com.google.solutions.jitaccess.core.services;

import java.io.IOException;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.adapters.PubSubAdaptor;

import com.google.protobuf.ByteString;
@ApplicationScoped
public class PubSubService {

    private final PubSubAdaptor pubSubAdaptor;

    public PubSubService(
            PubSubAdaptor pubSubAdaptor) {
        Preconditions.checkNotNull(pubSubAdaptor, "pubSubAdaptor");

        this.pubSubAdaptor = pubSubAdaptor;

    }

    @Produces(MediaType.TEXT_PLAIN)
    public void publishMessage(String projectId, String topicName,String data) throws IOException, InterruptedException {
//         var data = "test send";
         this.pubSubAdaptor.publish(projectId, topicName, data);
    }
}



