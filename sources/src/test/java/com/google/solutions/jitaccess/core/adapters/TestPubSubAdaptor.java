package com.google.solutions.jitaccess.core.adapters;

import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.solutions.jitaccess.core.data.MessageProperty;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class TestPubSubAdaptor {


    @Test
    public void whenUnauthenticated_ThenThrowsAccessDeniedException() {

        var expression = new Binding().set("start", "11am")
                .set("end", "12pm");
        var bindingDescription = String.format(
                "Self-approved, justification: %s",
                "test justification");
        var condition = new Binding().set("expression", expression)
                .set("title", "Activated").set("description", bindingDescription);


        var binding = new Binding().setRole("project/viewer")
                .set("project_id", "project1")
                .setMembers(List.of("example@example.com"))
                .set("conditions", condition);


        var adapter = new PubSubAdaptor(IntegrationTestEnvironment.INVALID_CREDENTIAL);

        assertThrows(
                ExecutionException.class,
                () -> adapter.publish(
                        IntegrationTestEnvironment.TOPIC_NAME,
                        new MessageProperty(
                                binding,
                                MessageProperty.MessageOrigin.APPROVAL)));
    }

    @Test
    public void whenAuthenticated_ThenPublishMessage() throws InterruptedException, IOException, ExecutionException {
        // if project id configured but no topic name, just skip the test
        if (IntegrationTestEnvironment.PROJECT_ID != null && IntegrationTestEnvironment.TOPIC_NAME == null) {
            return;
        }

        var expression = new Binding().set("start", "11am")
                .set("end", "12pm");
        var bindingDescription = String.format(
                "Self-approved, justification: %s",
                "test justification");
        var condition = new Binding().set("expression", expression)
                .set("title", "Activated").set("description", bindingDescription);


        var binding = new Binding().setRole("project/viewer")
                .set("project_id", "project1")
                .setMembers(List.of("example@example.com"))
                .set("conditions", condition);

        var adapter = new PubSubAdaptor(IntegrationTestEnvironment.APPLICATION_CREDENTIALS);

        assertTrue(
                adapter.publish(
                        IntegrationTestEnvironment.TOPIC_NAME, // makesure the topic is exists
                        new MessageProperty(
                                binding,
                                MessageProperty.MessageOrigin.APPROVAL)).matches("^[0-9]*$"));


    }

}
