package com.google.solutions.jitaccess.core.data;

import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.common.base.Objects;
import com.google.gson.Gson;
import com.google.common.base.Preconditions;

import java.util.Map;

public class MessageProperty {

    public final String user;

    public final Binding conditions;

    public final String role;

    public final String projectId;
    public final MessageOrigin origin;

    public MessageProperty(
                           String user,
                           Binding conditions,
                           String role,
                           String projectId,
                           MessageOrigin origin
                           ) {
        Preconditions.checkNotNull(user, "user");
        Preconditions.checkNotNull(conditions, "conditions");
        Preconditions.checkNotNull(role, "role");
        Preconditions.checkNotNull(projectId, "project");
        Preconditions.checkNotNull(origin, "origin");
        this.user = user;
        this.projectId = projectId;
        this.conditions = conditions;

        this.role = role;
        this.origin = origin;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageProperty)) return false;
        MessageProperty that = (MessageProperty) o;
        return Objects.equal(user, that.user) &&
                Objects.equal(conditions, that.conditions) &&
                Objects.equal(role, that.role) &&
                Objects.equal(projectId, that.projectId) &&
                origin == that.origin;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(user, conditions, role, projectId, origin);
    }

    @Override
    public String toString() {
        return new Gson().toJson(new Binding().set("data", new Binding()
                        .set("user", user)
                        .set("conditions", conditions)
                        .set("role", role)
                        .set("project_id", projectId))
                .set("attribute", origin));
    }

    public String getData() {
        return new Gson().toJson(new Binding()
                        .set("user", user)
                        .set("conditions", conditions)
                        .set("role", role)
                        .set("project_id", projectId));

    }

    public enum MessageOrigin {
        APPROVAL {
            public String toString() {
                return "jit-approval";
            }
        },
        ERROR {
            public String toString() {
                return "jit-error";
            }
        },
        NOTIFICATION {
            public String toString() {
                return "jit-notification";
            }
        },

        TEST {
            public String toString() {
                return "jit-test";
            }
        }

    }
}

