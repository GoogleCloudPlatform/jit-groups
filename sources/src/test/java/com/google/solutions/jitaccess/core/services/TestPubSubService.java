package com.google.solutions.jitaccess.core.services;

import com.google.pubsub.v1.TopicName;
import com.google.solutions.jitaccess.core.adapters.PubSubAdaptor;
import com.google.solutions.jitaccess.core.data.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import static org.mockito.Mockito.*;


public class TestPubSubService {
    private static final UserId SAMPLE_USER = new UserId("user-1", "user-1@example.com");

    private static final ProjectId SAMPLE_PROJECT_ID = new ProjectId("project-1");
    private static final String SAMPLE_TOPIC_NAME_RAW = "projects/123/topics/sample-topic";
    private static final TopicName SAMPLE_TOPIC_NAME = TopicName.parse(SAMPLE_TOPIC_NAME_RAW);

    private static final TopicName NULL_TOPIC_NAME = null;
    private static final String SAMPLE_ROLE = "roles/mock.role1";
    private static final MessageProperty SAMPLE_MESSAGE_PROPERTY = new MessageProperty(
            "",
            SAMPLE_USER.email,
            SAMPLE_PROJECT_ID.toString(),
            new HashMap<String, String>() {{}},
            SAMPLE_ROLE,
            MessageProperty.MessageOrigin.APPROVAL);



    @Test
    public void whenTopicNameNull_ThenDoNothing() throws Exception{
        var pubsubAdapter = Mockito.mock(PubSubAdaptor.class);
        // method will not be executed
        when(pubsubAdapter.publish(NULL_TOPIC_NAME, SAMPLE_MESSAGE_PROPERTY)).thenThrow(
                new ExecutionException(new Exception(""))
        );

        var service = new PubSubService(
                pubsubAdapter,
                new PubSubService.Options(""));
        service.publishMessage(SAMPLE_MESSAGE_PROPERTY);

        Mockito.verify(pubsubAdapter, times(0)).publish(
                NULL_TOPIC_NAME, SAMPLE_MESSAGE_PROPERTY);

    }

    @Test
    public void whenValidTopicName_ThenPublishMessage() throws Exception{
        var pubsubAdapter = Mockito.mock(PubSubAdaptor.class);

        when(pubsubAdapter.publish(SAMPLE_TOPIC_NAME, SAMPLE_MESSAGE_PROPERTY)).thenReturn(
                "1234"
        );
        var service = new PubSubService(
                pubsubAdapter,
                new PubSubService.Options(SAMPLE_TOPIC_NAME_RAW));
        service.publishMessage(SAMPLE_MESSAGE_PROPERTY);

        Mockito.verify(pubsubAdapter, times(1)).publish(
                SAMPLE_TOPIC_NAME, SAMPLE_MESSAGE_PROPERTY);

    }

}
