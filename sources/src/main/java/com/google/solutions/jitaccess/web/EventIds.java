//
// Copyright 2022 Google LLC
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

/**
 * Log event IDs used by this package.
 */
public class EventIds {
  public static final String API_AUTHENTICATE = "api.authenticate";
  public static final String API_CHECK_HEALTH = "api.health.check";
  public static final String API_VIEW_ENVIRONMENTS = "api.environments.view";
  public static final String API_RECONCILE_ENVIRONMENT = "api.environments.reconcile";
  public static final String API_VIEW_SYSTEMS = "api.systems.view";
  public static final String API_VIEW_GROUPS = "api.groups.view";
  public static final String API_JOIN_GROUP = "api.groups.join";
  public static final String GROUP_CONSTRAINT_FAILED = "api.groups.constraintFailed";
  public static final String API_APPROVE_JOIN = "api.groups.approve";
  public static final String STARTUP = "application.startup";
  public static final String LOAD_ENVIRONMENT = "application.environments.load";
}
