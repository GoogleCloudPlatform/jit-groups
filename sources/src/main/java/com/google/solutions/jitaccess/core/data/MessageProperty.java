package com.google.solutions.jitaccess.core.data;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import java.util.Map;

public class MessageProperty {
    public final String data;

    public final String user;

    public final String projectId;

    public final Map<String, String> conditions;


    public final String role;

    public final MessageOrigin origin;

    public MessageProperty(String data,
                           String user,
                           String projectId,
                           Map<String, String> conditions,
                           String role,
                           MessageOrigin origin
                           ) {
        Preconditions.checkNotNull(data);
        Preconditions.checkNotNull(user, "user");
        Preconditions.checkNotNull(projectId, "project");
        Preconditions.checkNotNull(conditions, "condition");
        Preconditions.checkNotNull(role, "role");
        Preconditions.checkNotNull(origin);
        this.data = data;
        this.user = user;
        this.projectId = projectId;
        this.conditions = conditions;
        this.role = role;
        this.origin = origin;

    }

    @Override
    public String toString() {
        return String.format("data:%s user:%s project:%s condition:%s origin:%s",
                data, user, projectId, conditions, origin);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        var that = (MessageProperty) o;
        return  Objects.equal(data, that.data)
                && Objects.equal(user, that.user)
                && Objects.equal(projectId, that.projectId)
                && Objects.equal(conditions, that.conditions)
                && origin == that.origin;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(data, user, projectId, conditions, origin);
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

