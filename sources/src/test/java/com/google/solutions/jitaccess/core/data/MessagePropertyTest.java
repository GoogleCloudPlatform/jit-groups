package com.google.solutions.jitaccess.core.data;

import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagePropertyTest {

    @Test
    void testToString() {

        var expression = new Binding().set("start", "11am")
                .set("end", "12pm");
        var bindingDescription = String.format(
                "Self-approved, justification: %s",
                "test justification");
        var condition = new Binding().set("expression", expression)
                .set("title", "Activated")
                .set("description", bindingDescription);


        var binding = new Binding().setRole("project/viewer")
                .set("project_id", "project1")
                .setMembers(List.of("example@example.com"))
                .set("conditions", condition);


        assertEquals("{\"data\":{\"members\":[\"example@example.com\"],\"role\":\"project/viewer\",\"project_id\":\"project1\",\"conditions\":{\"expression\":{\"start\":\"11am\",\"end\":\"12pm\"},\"title\":\"Activated\",\"description\":\"Self-approved, justification: test justification\"}},\"attribute\":\"APPROVAL\"}",
                new MessageProperty(
                        binding,
                        MessageProperty.MessageOrigin.APPROVAL).toString());
    }

    @Test
    void whenObjectAreEquivalent_ThenEqualsReturnsTrue() {

        var expression = new Binding().set("start", "11am")
                .set("end", "12pm");
        var bindingDescription = String.format(
                "Self-approved, justification: %s",
                "test justification");
        var condition = new Binding().set("expression", expression)
                .set("title", "Activated")
                .set("description", bindingDescription);


        var binding1 = new Binding().setRole("project/viewer")
                .set("project_id", "project1")
                .setMembers(List.of("example@example.com"))
                .set("conditions", condition);

        var binding2 = new Binding().setRole("project/viewer")
                .set("project_id", "project1")
                .setMembers(List.of("example@example.com"))
                .set("conditions", condition);


        MessageProperty messageProperty1 = new MessageProperty(
                binding1,
                MessageProperty.MessageOrigin.APPROVAL);

        MessageProperty messageProperty2 = new MessageProperty(
                binding2,
                MessageProperty.MessageOrigin.APPROVAL);

        assertEquals(messageProperty1, messageProperty2);
        assertEquals(messageProperty1.hashCode(), messageProperty2.hashCode());
    }

}