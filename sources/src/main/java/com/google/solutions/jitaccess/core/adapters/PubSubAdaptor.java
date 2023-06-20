package com.google.solutions.jitaccess.core.adapters;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;

import static io.quarkus.arc.ComponentsProvider.LOG;



@ApplicationScoped
public class PubSubAdaptor {

    private PubSubAdaptor() {}

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public void publish(String projectId, String topicName, String data) throws IOException, InterruptedException {

        var publisher = Publisher.newBuilder(TopicName.of(projectId, topicName))
                .build();

        try {
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(ByteString.copyFrom(data.getBytes())).build();
            ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);// Publish the message
            ApiFutures.addCallback(messageIdFuture, new ApiFutureCallback<String>() {// Wait for message submission and log the result
                public void onSuccess(String messageId) {
                    LOG.infov("published with message id {0}", messageId);
                }

                public void onFailure(Throwable t) {
                    LOG.warnv("failed to publish: {0}", t);
                }
            }, MoreExecutors.directExecutor());
        } finally {
            publisher.shutdown();
            publisher.awaitTermination(1, TimeUnit.MINUTES);
        }
    }



}
