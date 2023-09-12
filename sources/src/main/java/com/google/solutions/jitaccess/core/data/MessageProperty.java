//
// Copyright 2023 Google LLC
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.google.solutions.jitaccess.core.data;

import com.google.api.client.json.GenericJson;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;

public class MessageProperty {

    public final GenericJson payload;
    public final MessageOrigin origin;

    public MessageProperty(GenericJson payload, MessageOrigin origin) {

        Preconditions.checkNotNull(payload, "payload");
        Preconditions.checkNotNull(origin, "origin");
        this.payload = payload;
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
        return new Gson().toJson(new GenericJson()
                .set("data", payload)
                .set("attribute", origin));
    }

    public String getData() {
        return new Gson().toJson(new GenericJson()
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

