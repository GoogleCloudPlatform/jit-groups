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

package com.google.solutions.jitaccess.auth;

import com.google.api.services.cloudidentity.v1.model.*;
import com.google.solutions.jitaccess.apis.Domain;
import com.google.solutions.jitaccess.apis.Logger;
import com.google.solutions.jitaccess.apis.clients.CloudIdentityGroupsClient;
import com.google.solutions.jitaccess.apis.clients.ResourceNotFoundException;
import com.google.solutions.jitaccess.catalog.EventIds;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestSubjectResolver {
  private static final Domain SAMPLE_DOMAIN = new Domain("example.com", Domain.Type.PRIMARY);
  private static final EndUserId SAMPLE_USER = new EndUserId("user@example.com");
  private static final JitGroupId SAMPLE_JITGROUP = new JitGroupId("env-1", "sys-1", "grp-1");
  private static final GroupId SAMPLE_GROUP = new GroupId("other@" + SAMPLE_DOMAIN);
  private static final Executor EXECUTOR = command -> command.run();

  //---------------------------------------------------------------------------
  // resolveJitGroupMemberships
  //---------------------------------------------------------------------------

  @Test
  public void resolveJitGroupMemberships_whenGroupHasMultipleExpiries_thenPrincipalUsesMinimum() throws Exception {
    var mapping = new GroupMapping(SAMPLE_DOMAIN);
    var membershipId = new CloudIdentityGroupsClient.MembershipId("m1");

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.getMembership(eq(membershipId)))
      .thenReturn(new Membership()
        .setPreferredMemberKey(new EntityKey().setId(SAMPLE_USER.email))
        .setRoles(List.of(
          new MembershipRole()
            .setName("OWNER"),
          new MembershipRole()
            .setName("MANAGER")
            .setExpiryDetail(new ExpiryDetail().setExpireTime("2031-01-01T00:00:00Z")),
          new MembershipRole()
            .setName("MEMBER")
            .setExpiryDetail(new ExpiryDetail().setExpireTime("2030-01-01T00:00:00Z")))));

    var resolver = new SubjectResolver(
      groupsClient,
      mapping,
      new Directory(SAMPLE_DOMAIN),
      EXECUTOR,
      Mockito.mock(Logger.class));

    var principals = resolver.resolveJitGroupMemberships(
      SAMPLE_USER,
      List.of(new SubjectResolver.UnresolvedMembership(
        mapping.groupFromJitGroup(SAMPLE_JITGROUP),
        membershipId)));

    assertEquals(1, principals.size());

    var principal = principals.stream().findFirst().get();
    assertEquals(SAMPLE_JITGROUP, principal.id());
    assertEquals(Instant.parse("2030-01-01T00:00:00Z"), principal.expiry());
  }

  @Test
  public void resolveJitGroupMemberships_whenGroupLacksExpiry_thenGrouplIsIgnored() throws Exception {
    var mapping = new GroupMapping(SAMPLE_DOMAIN);
    var membershipId = new CloudIdentityGroupsClient.MembershipId("m1");

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.getMembership(eq(membershipId)))
      .thenReturn(new Membership()
        .setPreferredMemberKey(new EntityKey().setId(SAMPLE_USER.email))
        .setRoles(List.of(
          new MembershipRole()
            .setName("OWNER"))));

    var logger = Mockito.mock(Logger.class);
    var resolver = new SubjectResolver(
      groupsClient,
      mapping,
      new Directory(SAMPLE_DOMAIN),
      EXECUTOR,
      logger);

    var principals = resolver.resolveJitGroupMemberships(
      SAMPLE_USER,
      List.of(new SubjectResolver.UnresolvedMembership(
        mapping.groupFromJitGroup(SAMPLE_JITGROUP),
        membershipId)));

    assertEquals(0, principals.size());
    verify(logger, times(1)).warn(eq(EventIds.SUBJECT_RESOLUTION), anyString());
  }

  @Test
  public void resolveJitGroupMemberships_whenLookupFails_thenGroupIsIgnored() throws Exception {
    var mapping = new GroupMapping(SAMPLE_DOMAIN);
    var membershipId = new CloudIdentityGroupsClient.MembershipId("m1");

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.getMembership(eq(membershipId)))
      .thenThrow(new ResourceNotFoundException("mock"));

    var logger = Mockito.mock(Logger.class);
    var resolver = new SubjectResolver(
      groupsClient,
      mapping,
      new Directory(SAMPLE_DOMAIN),
      EXECUTOR,
      logger);

    var principals = resolver.resolveJitGroupMemberships(
      SAMPLE_USER,
      List.of(new SubjectResolver.UnresolvedMembership(
        mapping.groupFromJitGroup(SAMPLE_JITGROUP),
        membershipId)));

    assertEquals(0, principals.size());
  }

  //---------------------------------------------------------------------------
  // resolveGroupPrincipals
  //---------------------------------------------------------------------------

  @Test
  public void resolveGroupPrincipals() throws Exception {
    var mapping = new GroupMapping(SAMPLE_DOMAIN);

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.listMembershipsByUser(eq(SAMPLE_USER)))
      .thenReturn(List.of(
        new MembershipRelation()
          .setGroupKey(new EntityKey().setId(SAMPLE_GROUP.email)),
        new MembershipRelation()
          .setGroupKey(new EntityKey().setId(mapping.groupFromJitGroup(SAMPLE_JITGROUP).email))
          .setMembership("m1")));
    when(groupsClient.getMembership(eq(new CloudIdentityGroupsClient.MembershipId("m1"))))
      .thenReturn(new Membership()
        .setPreferredMemberKey(new EntityKey().setId(SAMPLE_USER.email))
        .setRoles(List.of(
          new MembershipRole()
            .setName("MEMBER")
            .setExpiryDetail(new ExpiryDetail().setExpireTime("2030-01-01T00:00:00Z")))));

    var resolver = new SubjectResolver(
      groupsClient,
      mapping,
      new Directory(SAMPLE_DOMAIN),
      EXECUTOR,
      Mockito.mock(Logger.class));

    var principals = resolver.resolveGroupPrincipals(SAMPLE_USER)
      .stream()
      .map(p -> p.id())
      .collect(Collectors.toSet());

    assertEquals(2, principals.size());
    assertTrue(principals.contains(SAMPLE_GROUP), "other group");
    assertTrue(principals.contains(SAMPLE_JITGROUP), "JIT group");
  }

  //---------------------------------------------------------------------------
  // resolvePrincipals
  //---------------------------------------------------------------------------

  @Test
  public void resolvePrincipals_whenUserFromInternalDirectory() throws Exception {
    var mapping = new GroupMapping(SAMPLE_DOMAIN);

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.listMembershipsByUser(eq(SAMPLE_USER)))
      .thenReturn(List.of());

    var resolver = new SubjectResolver(
      groupsClient,
      mapping,
      new Directory(SAMPLE_DOMAIN),
      EXECUTOR,
      Mockito.mock(Logger.class));

    var principals = resolver.resolvePrincipals(SAMPLE_USER, new Directory(SAMPLE_DOMAIN))
      .stream()
      .map(p -> p.id())
      .collect(Collectors.toSet());

    assertEquals(4, principals.size());
    assertTrue(principals.contains(SAMPLE_USER), "user principal");
    assertTrue(principals.contains(ClassPrincipalSet.IAP_USERS), "All");
    assertTrue(principals.contains(ClassPrincipalSet.INTERNAL_USERS), "Internal");
    assertTrue(principals.contains(new CloudIdentityDirectoryPrincipalSet(new Directory(SAMPLE_DOMAIN))));
  }

  @Test
  public void resolvePrincipals_whenUserFromDifferentDirectory() throws Exception {
    var mapping = new GroupMapping(SAMPLE_DOMAIN);

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.listMembershipsByUser(eq(SAMPLE_USER)))
      .thenReturn(List.of());

    var resolver = new SubjectResolver(
      groupsClient,
      mapping,
      new Directory(SAMPLE_DOMAIN),
      EXECUTOR,
      Mockito.mock(Logger.class));

    var principals = resolver.resolvePrincipals(SAMPLE_USER, new Directory("other.tld"))
      .stream()
      .map(p -> p.id())
      .collect(Collectors.toSet());

    assertEquals(4, principals.size());
    assertTrue(principals.contains(SAMPLE_USER), "user principal");
    assertTrue(principals.contains(ClassPrincipalSet.IAP_USERS), "All");
    assertTrue(principals.contains(ClassPrincipalSet.EXTERNAL_USERS), "External");
    assertTrue(principals.contains(new CloudIdentityDirectoryPrincipalSet(new Directory("other.tld"))));
  }

  @Test
  public void resolvePrincipals_whenUserIsConsumer() throws Exception {
    var mapping = new GroupMapping(SAMPLE_DOMAIN);

    var groupsClient = Mockito.mock(CloudIdentityGroupsClient.class);
    when(groupsClient.listMembershipsByUser(eq(SAMPLE_USER)))
      .thenReturn(List.of());

    var resolver = new SubjectResolver(
      groupsClient,
      mapping,
      new Directory(SAMPLE_DOMAIN),
      EXECUTOR,
      Mockito.mock(Logger.class));

    var principals = resolver.resolvePrincipals(SAMPLE_USER, Directory.CONSUMER)
      .stream()
      .map(p -> p.id())
      .collect(Collectors.toSet());

    assertEquals(3, principals.size());
    assertTrue(principals.contains(SAMPLE_USER), "user principal");
    assertTrue(principals.contains(ClassPrincipalSet.IAP_USERS), "All");
    assertTrue(principals.contains(ClassPrincipalSet.EXTERNAL_USERS), "External");
  }
}
