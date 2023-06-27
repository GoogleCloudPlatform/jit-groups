package com.google.solutions.jitaccess.core.data;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessagePropertyTest {

    @Test
    void testToString() {
        Map<String, String> map = new HashMap<>();
        map.put("start", "11am");

        assertEquals("data:test message user:service-account1@google.com project:project-1 condition:{start=11am} origin:jit-approval", new MessageProperty("test message",
                "service-account1@google.com",
                "project-1",
                map,
                "project/viewer",
                MessageProperty.MessageOrigin.APPROVAL
        ).toString());
    }

    @Test
    void whenObjectAreEquivalent_ThenEqualsReturnsTrue() {
        Map<String, String> map = new HashMap<>();
        map.put("start", "11am");

        MessageProperty messageProperty1 = new MessageProperty("test message",
                "service-account1@google.com",
                "project-1",
                map,
                "project/viewer",
                MessageProperty.MessageOrigin.APPROVAL
                );

        MessageProperty messageProperty2 = new MessageProperty("test message",
                "service-account1@google.com",
                "project-1",
                map,
                "project/viewer",
                MessageProperty.MessageOrigin.APPROVAL
        );

        assertEquals(messageProperty1, messageProperty2);
        assertEquals(messageProperty1.hashCode(), messageProperty2.hashCode());
    }

    @Test
    public void whenObjectAreSame_ThenEqualsReturnsTrue() {
        Map<String, String> map = new HashMap<>();
        map.put("start", "11am");

        MessageProperty messageProperty1 = new MessageProperty("test message",
                "service-account1@google.com",
                "project-1",
                map,
                "project/viewer",
                MessageProperty.MessageOrigin.APPROVAL
        );

        assertEquals(messageProperty1, messageProperty1);
    }

    @Test
    public void whenObjectAreMotEquivalent_ThenEqualsReturnsFalse() {
        Map<String, String> map = new HashMap<>();
        map.put("start", "11am");

        MessageProperty messageProperty1 = new MessageProperty("test message",
                "service-account1@google.com",
                "project-1",
                map,
                "project/viewer",
                MessageProperty.MessageOrigin.APPROVAL
        );

        MessageProperty messageProperty2 = new MessageProperty("test message",
                "service-account1@google.com",
                "project-2",
                map,
                "project/viewer",
                MessageProperty.MessageOrigin.APPROVAL
        );

        assertNotEquals(messageProperty1, messageProperty2);
        assertNotEquals(messageProperty1.hashCode(), messageProperty2.hashCode());

    }

    @Test
    public void whenObjectIsNull_ThenEqualsReturnsFalse() {
        Map<String, String> map = new HashMap<>();
        map.put("start", "11am");

        MessageProperty messageProperty1 = new MessageProperty("test message",
                "service-account1@google.com",
                "project-1",
                map,
                "project/viewer",
                MessageProperty.MessageOrigin.APPROVAL
        );

        assertNotEquals(null, messageProperty1);
    }

    @Test
    public void whenObjectIsDifferentType_ThenEqualsReturnsFalse() {
        Map<String, String> map = new HashMap<>();
        map.put("start", "11am");

        MessageProperty messageProperty1 = new MessageProperty("test message",
                "service-account1@google.com",
                "project-1",
                map,
                "project/viewer",
                MessageProperty.MessageOrigin.APPROVAL
        );

        assertNotEquals("null", messageProperty1);
    }
}