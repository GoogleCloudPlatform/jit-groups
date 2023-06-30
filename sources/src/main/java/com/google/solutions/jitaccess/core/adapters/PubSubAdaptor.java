package com.google.solutions.jitaccess.core.adapters;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import com.google.solutions.jitaccess.core.data.MessageProperty;
import com.google.gson.Gson;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static io.quarkus.arc.ComponentsProvider.LOG;


@ApplicationScoped
public class PubSubAdaptor {
    private final GoogleCredentials credentials;
    public PubSubAdaptor(GoogleCredentials credentials) {
        Preconditions.checkNotNull(credentials, "credentials");
        this.credentials = credentials;
    }

    private Publisher createClient(TopicName topicName) throws IOException {
        try {
            if(this.credentials != null) {
                return Publisher.newBuilder(topicName).setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();
            }
            return Publisher.newBuilder(topicName).build();
        } catch (IOException e) {
            throw new IOException("Creating a CloudAsset client failed", e);
        }
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String publish(TopicName topicName, MessageProperty messageProperty) throws IOException, InterruptedException, ExecutionException {

        var publisher = createClient(topicName);

        try {
            Map<String, String> messageAttribute = new HashMap<>() {{
                put("user", messageProperty.user);
                //put("start", messageProperty.start);
                //put("end", messageProperty.end);
                put("projectId", messageProperty.projectId);
                put("condition", new Gson().toJson(messageProperty.conditions));
                put("origin", messageProperty.origin.toString());
//                put("role")

            }};
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFrom(messageProperty.data.getBytes()))
                    .putAllAttributes(messageAttribute).build();
            ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);// Publish the message
            String messageId = messageIdFuture.get();

            return messageId;
        } finally {
            publisher.shutdown();
            publisher.awaitTermination(1, TimeUnit.MINUTES);
        }
    }


}
