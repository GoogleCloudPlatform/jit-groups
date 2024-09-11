//
// Copyright 2024 Google LLC
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

package com.google.solutions.jitaccess.web;

import com.google.solutions.jitaccess.apis.OrganizationId;
import com.google.solutions.jitaccess.apis.clients.GroupKey;
import com.google.solutions.jitaccess.auth.GroupId;
import com.google.solutions.jitaccess.auth.JitGroupId;
import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Generates links to various consoles.
 */
public class Consoles {
  private final @NotNull OrganizationId organizationId;

  public Consoles(@NotNull OrganizationId organizationId) {
    this.organizationId = organizationId;
  }

  public CloudConsole cloudConsole() {
    return new CloudConsole();
  }

  public AdminConsole adminConsole() {
    return new AdminConsole();
  }

  public GoogleGroupsConsole groupsConsole() {
    return new GoogleGroupsConsole();
  }

  /**
   * Generates links to the Cloud Console.
   */
  public class CloudConsole {
    private CloudConsole() {
    }

    /**
     * Get link to group details page.
     */
    public String groupDetails(@NotNull GroupKey group) {
      return String.format(
        "https://console.cloud.google.com/iam-admin/groups/%s?organizationId=%s",
        group.id(),
        Consoles.this.organizationId.id());
    }

    public String groupAuditLogs(
      @NotNull JitGroupId groupId,
      @NotNull Instant startDate) {
      var query = String.format(
        "labels.\"%s\"=\"%s\"",
        OperationAuditTrail.LABEL_GROUP_ID,
        groupId);

      return String.format(
        "https://console.cloud.google.com/logs/query;query=%s;startTime=%s",
        URLEncoder.encode(query),
        startDate
          .truncatedTo(ChronoUnit.SECONDS)
          .atOffset(ZoneOffset.UTC));
    }
  }

  /**
   * Generates links to the Admin Console.
   */
  public static class AdminConsole {
    private AdminConsole() {
    }

    /**
     * Get link to group details page.
     */
    public String groupDetails(@NotNull GroupKey group) {
      return String.format("https://admin.google.com/ac/groups/%s", group.id());
    }
  }

  /**
   * Generates links to the Google Groups Console.
   */
  public static class GoogleGroupsConsole {
    private GoogleGroupsConsole() {
    }

    /**
     * Get link to group details page.
     */
    public String groupDetails(@NotNull GroupId group) {
      var components = group.components();
      return String.format(
        "https://groups.google.com/a/%s/g/%s/about",
        components.domain(),
        components.name());
    }
  }
}
