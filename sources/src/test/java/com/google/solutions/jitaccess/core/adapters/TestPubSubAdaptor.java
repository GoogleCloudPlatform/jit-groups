package com.google.solutions.jitaccess.core.adapters;

import com.google.solutions.jitaccess.core.data.MessageProperty;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.*;

class TestPubSubAdaptor {

    @Test
    public void whenUnauthenticated_ThenThrowsAccessDeniedException() {
        var adapter = new PubSubAdaptor(IntegrationTestEnvironment.INVALID_CREDENTIAL);

        assertThrows(
            ExecutionException.class,
            () -> adapter.publish(
                IntegrationTestEnvironment.PROJECT_ID.toString(),
                "fake-topic",
                new MessageProperty(
                    "",
                    "bob@example.com",
                    IntegrationTestEnvironment.PROJECT_ID.toString(),
                    new HashMap<String, String>(),
                    "role/fake-role",
                    MessageProperty.MessageOrigin.APPROVAL)));
    }

    @Test
    public void whenAuthenticated_ThenPublishMessage() throws IOException, ExecutionException, InterruptedException {
        // if project id configured but no topic name, just skip the test
        if(IntegrationTestEnvironment.PROJECT_ID !=null && IntegrationTestEnvironment.TOPIC_NAME == "") {
            return;
        }

        var adapter = new PubSubAdaptor(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);
        assertTrue(
            adapter.publish(
                IntegrationTestEnvironment.PROJECT_ID.toString(),
                IntegrationTestEnvironment.TOPIC_NAME, // makesure the topic is exists
                new MessageProperty(
                    "",
                    "bob@example.com",
                    IntegrationTestEnvironment.PROJECT_ID.toString(),
                    new HashMap<String, String>(),
                    "role/fake-role",
                    MessageProperty.MessageOrigin.TEST)).matches("^[0-9]*$"));


    }

}