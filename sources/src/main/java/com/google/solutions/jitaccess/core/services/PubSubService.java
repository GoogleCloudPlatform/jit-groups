package com.google.solutions.jitaccess.core.services;

import com.google.common.base.Preconditions;
import com.google.pubsub.v1.TopicName;
import com.google.solutions.jitaccess.core.adapters.PubSubAdaptor;
import com.google.solutions.jitaccess.core.data.MessageProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class PubSubService {

    private final PubSubAdaptor pubSubAdaptor;

    private final Options options;

    // TODO: where is it set?
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
        if(this.getOptions().topicName != null) {
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
