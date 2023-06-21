package com.google.solutions.jitaccess.core.services;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.adapters.PubSubAdaptor;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

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
    public void publishMessage(String data) throws IOException, InterruptedException {
        this.pubSubAdaptor.publish(options.projectId, options.topicName, data);
    }

    // -------------------------------------------------------------------------
    // Inner classes.
    // -------------------------------------------------------------------------

    public Options getOptions() {
        return options;
    }

    public static class Options {
        /**
         * ProjectId, TopicName, folder/ID, or project/ID
         */
        public final String projectId;
        public final String topicName;

        /**
         * Search inherited IAM policies
         */
        public Options(String projectId, String topicName) {
            this.projectId = projectId;
            this.topicName = topicName;
        }
    }

}
