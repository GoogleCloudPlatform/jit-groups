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

package com.google.solutions.jitaccess.web.actions;

import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.util.Exceptions;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.LogEvents;
import com.google.solutions.jitaccess.web.iap.IapPrincipal;
import jakarta.enterprise.context.Dependent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * List projects that the calling user can access.
 */
public class ListScopesAction<
  TEntitlementId extends EntitlementId,
  TScopeId extends ResourceId,
  TUserContext extends CatalogUserContext
  > extends AbstractAction {

  private final @NotNull Catalog<TEntitlementId, TScopeId, TUserContext> catalog;

  public ListScopesAction(
    @NotNull LogAdapter logAdapter,
    @NotNull Catalog<TEntitlementId, TScopeId, TUserContext> catalog
  ) {
    super(logAdapter);
    this.catalog = catalog;
  }

  public @NotNull ListScopesAction.ResponseEntity execute(
    @NotNull IapPrincipal iapPrincipal
  ) throws AccessException, IOException {
    var userContext = this.catalog.createContext(iapPrincipal.email());

    try {
      var projects = this.catalog.listScopes(userContext);

      this.logAdapter
        .newInfoEntry(
          LogEvents.API_LIST_PROJECTS,
          String.format("Found %d available projects", projects.size()))
        .write();

      return new ResponseEntity(projects
        .stream()
        .map(ResourceId::id)
        .collect(Collectors.toCollection(TreeSet::new)));
    }
    catch (Exception e) {
      this.logAdapter
        .newErrorEntry(
          LogEvents.API_LIST_PROJECTS,
          String.format("Listing available projects failed: %s", Exceptions.getFullMessage(e)))
        .write();

      throw new AccessDeniedException("Listing available projects failed, see logs for details");
    }
  }

  public static class ResponseEntity {
    public final @NotNull Set<String> projects;

    private ResponseEntity(@NotNull SortedSet<String> projects) {
      Preconditions.checkNotNull(projects, "projects");
      this.projects = projects;
    }
  }
}
