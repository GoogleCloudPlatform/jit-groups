package com.google.solutions.jitaccess.core.data;

import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.common.base.Objects;
import com.google.gson.Gson;
import com.google.common.base.Preconditions;

import java.util.Map;

public class MessageProperty {

//    public final String user;
//
//    public final Binding conditions;
//
//    public final String role;
//
//    public final String projectId;

    public final Binding payload;
    public final MessageOrigin origin;

    public MessageProperty(
//                           String user,
//                           Binding conditions,
//                           String role,
//                           String projectId,
                           Binding payload,
                           MessageOrigin origin
                           ) {
        // Preconditions.checkNotNull(user, "user");
        // Preconditions.checkNotNull(conditions, "conditions");
        // Preconditions.checkNotNull(role, "role");

//        Preconditions.checkNotNull(jwt, "jwt");
        Preconditions.checkNotNull(payload, "payload");
        Preconditions.checkNotNull(origin, "origin");
        this.payload = payload;
//        this.jwt = jwt;
        this.origin = origin;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageProperty)) return false;
        MessageProperty that = (MessageProperty) o;
        return Objects.equal(payload, that.payload) &&
                origin == that.origin;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(payload, origin);
    }

    @Override
    public String toString() {
        return new Gson().toJson(new Binding()
                .set("data", payload)
                .set("attribute", origin));
    }

    public String getData() {
        return new Gson().toJson(new Binding()
                        .set("payload", payload));
    }

    public enum MessageOrigin {
        APPROVAL {
            public String toString() {
                return "jit-approval";
            }
        },
        BINDING {
            public String toString() {
                return "jit-binding";
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

