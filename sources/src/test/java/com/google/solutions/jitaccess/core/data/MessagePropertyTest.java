package com.google.solutions.jitaccess.core.data;

import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessagePropertyTest {

    @Test
    void testToString() {

        var expression = new Binding().set("start", "11am")
                .set("end", "12pm");

        var bindingDescription = String.format(
                "Self-approved, justification: %s",
                "test justification");

        var conditions = new Binding().set("expression", expression)
                .set("title", "Activated")
                .set("description", bindingDescription);

        assertEquals("{\"data\":{\"role\":\"project/viewer\",\"user\":\"example@example.com\",\"conditions\":" +
                "{\"expression\":{\"start\":\"11am\",\"end\":\"12pm\"},\"title\":\"Activated\",\"description\":" +
                "\"Self-approved, justification: test justification\"},\"project_id\":\"project1\"},\"attribute\":" +
                "\"APPROVAL\"}", new MessageProperty(
                "example@example.com",
                conditions,
                "project/viewer",
                "project1",
                MessageProperty.MessageOrigin.APPROVAL).toString());
    }

    @Test
    void whenObjectAreEquivalent_ThenEqualsReturnsTrue() {
        var expression = new Binding().set("start", "11am")
                .set("end", "12pm");

        var bindingDescription = String.format(
                "Self-approved, justification: %s",
                "test justification");

        var conditions = new Binding().set("expression", expression)
                .set("title", "Activated")
                .set("description", bindingDescription);


        MessageProperty messageProperty1 = new MessageProperty(
                "example@example.com",
                conditions,
                "project/viewer",
                "project1",
                MessageProperty.MessageOrigin.APPROVAL);

        MessageProperty messageProperty2 = new MessageProperty(
                "example@example.com",
                conditions,
                "project/viewer",
                "project1",
                MessageProperty.MessageOrigin.APPROVAL);

        assertEquals(messageProperty1, messageProperty2);
        assertEquals(messageProperty1.hashCode(), messageProperty2.hashCode());
    }

    @Test
    public void whenObjectAreSame_ThenEqualsReturnsTrue() {
        var expression = new Binding().set("start", "11am")
                .set("end", "12pm");

        var bindingDescription = String.format(
                "Self-approved, justification: %s",
                "test justification");

        var conditions = new Binding().set("expression", expression)
                .set("title", "Activated")
                .set("description", bindingDescription);

        MessageProperty messageProperty1 = new MessageProperty(
                "example@example.com",
                conditions,
                "project/viewer",
                "project1",
                MessageProperty.MessageOrigin.APPROVAL);

        assertEquals(messageProperty1, messageProperty1);
    }

    @Test
    public void whenObjectAreMotEquivalent_ThenEqualsReturnsFalse() {
        var expression = new Binding().set("start", "11am")
                .set("end", "12pm");

        var bindingDescription = String.format(
                "Self-approved, justification: %s",
                "test justification");

        var conditions = new Binding().set("expression", expression)
                .set("title", "Activated")
                .set("description", bindingDescription);

        MessageProperty messageProperty1 = new MessageProperty(
                "example@example.com",
                conditions,
                "project/viewer",
                "project1",
                MessageProperty.MessageOrigin.APPROVAL);

        MessageProperty messageProperty2 = new MessageProperty(
                "example@example.com",
                conditions,
                "project/viewer",
                "project2",
                MessageProperty.MessageOrigin.APPROVAL);


        assertNotEquals(messageProperty1, messageProperty2);
        assertNotEquals(messageProperty1.hashCode(), messageProperty2.hashCode());

    }

    @Test
    public void whenObjectIsNull_ThenEqualsReturnsFalse() {
        var expression = new Binding().set("start", "11am")
                .set("end", "12pm");

        var bindingDescription = String.format(
                "Self-approved, justification: %s",
                "test justification");

        var conditions = new Binding().set("expression", expression)
                .set("title", "Activated")
                .set("description", bindingDescription);

        MessageProperty messageProperty1 = new MessageProperty(
                "example@example.com",
                conditions,
                "project/viewer",
                "project1",
                MessageProperty.MessageOrigin.APPROVAL);

        assertNotEquals(null, messageProperty1);
    }

    @Test
    public void whenObjectIsDifferentType_ThenEqualsReturnsFalse() {
        var expression = new Binding().set("start", "11am")
                .set("end", "12pm");

        var bindingDescription = String.format(
                "Self-approved, justification: %s",
                "test justification");

        var conditions = new Binding().set("expression", expression)
                .set("title", "Activated")
                .set("description", bindingDescription);

        MessageProperty messageProperty1 = new MessageProperty(
                "example@example.com",
                conditions,
                "project/viewer",
                "project1",
                MessageProperty.MessageOrigin.APPROVAL);

        assertNotEquals("null", messageProperty1);
    }
}