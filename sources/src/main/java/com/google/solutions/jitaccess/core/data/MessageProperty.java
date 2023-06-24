package com.google.solutions.jitaccess.core.data;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class MessageProperty {
    public final String data;

    public final String start;
    public final String end;

    public final String user;

    public final String projectId;

    public final String condition;
    public final MessageOrigin origin;

    public MessageProperty(String data,
                           String start,
                           String end,
                           String user,
                           String projectId,
                           String condition,
                           MessageOrigin origin
                           ) {
        Preconditions.checkNotNull(data);
        Preconditions.checkNotNull(start, "start time");
        Preconditions.checkNotNull(end, "end time");
        Preconditions.checkNotNull(user, "user");
        Preconditions.checkNotNull(projectId, "project");
        Preconditions.checkNotNull(condition, "condition");
        Preconditions.checkNotNull(origin);
        this.data = data;
        this.start = start;
        this.end = end;
        this.user = user;
        this.projectId = projectId;
        this.condition = condition;
        this.origin = origin;

    }

    @Override
    public String toString() {
        return String.format("data:%s start:%s end:%s user:%s project:%s condition:%s origin:%s",
                data, start, end, user, projectId, condition, origin);
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
                && Objects.equal(start, that.start)
                && Objects.equal(end, that.end)
                && Objects.equal(user, that.user)
                && Objects.equal(projectId, that.projectId)
                && Objects.equal(condition, that.condition)
                && origin == that.origin;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(data, start, end, user, projectId, condition, origin);
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

    }
}

