package com.google.solutions.jitaccess.core.catalog.group;

import com.google.api.services.cloudidentity.v1.model.Membership;
import com.google.solutions.jitaccess.cel.TimeSpan;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.GroupEmail;
import com.google.solutions.jitaccess.core.auth.PrincipalIdentifier;
import com.google.solutions.jitaccess.core.auth.Subject;
import com.google.solutions.jitaccess.core.auth.UserEmail;
import com.google.solutions.jitaccess.core.clients.CloudIdentityGroupsClient;
import jakarta.ws.rs.NotFoundException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class User implements Subject {
  private final @NotNull UserEmail id;
  private final @NotNull Set<ActiveEntitlement> groups;
  private final @NotNull Set<PrincipalIdentifier> allPrincipals;

  User(
    @NotNull UserEmail id,
    @NotNull Set<ActiveEntitlement> groups
  ) {
    this.id = id;
    this.groups = groups;
    this.allPrincipals = Stream
      .concat(
        groups.stream().map(m -> m.id().group()),
        Stream.of(id))
      .collect(Collectors.toSet());
  }

  /**
   * @return the user ID/primary email.
   */
  @NotNull public UserEmail id() {
    return id;
  }

  /**
   * @return direct group memberships of this user.
   */
  @NotNull Set<ActiveEntitlement> activeEntitlement() {
    return groups;
  }

  /**
   * @return full set of principals, including groups.
   */
  public @NotNull Set<PrincipalIdentifier> principals() {
    return this.allPrincipals;
  }

  //---------------------------------------------------------------------------
  // Inner classes.
  //---------------------------------------------------------------------------

  record ActiveEntitlement(
    @NotNull GroupMembership id,
    @Nullable TimeSpan validity
  ) {
  }

  public static class Resolver { // TODO: test
    private final @NotNull CloudIdentityGroupsClient groupsClient;
    private final @NotNull GroupMapper mapper;
    private final @NotNull Executor executor;

    public Resolver(
      @NotNull CloudIdentityGroupsClient groupsClient,
      @NotNull GroupMapper mapper,
      @NotNull Executor executor
    ) {
      this.groupsClient = groupsClient;
      this.mapper = mapper;
      this.executor = executor;
    }

    public User lookup(
      @NotNull UserEmail user
      ) throws AccessException, IOException {
      //
      // Find the user's direct group memberships.
      //
      var membershipRelations = this.groupsClient
        .listMembershipsByUser(user)
        .stream()
        .filter(m -> this.mapper.isMappedGroup(new GroupEmail(m.getGroup())))
        .toList();

      //
      // The API does not return expiry details, so we have to perform
      // extra lookups.
      //

      assert membershipRelations
        .stream()
        .flatMap(m -> m.getRoles().stream())
        .allMatch(r -> r.getExpiryDetail() == null);

      List<CompletableFuture<Membership>> membershipFutures = membershipRelations
        .stream()
        .map(r -> new CloudIdentityGroupsClient.MembershipId(r.getMembership()))
        .map(membershipId -> ThrowingCompletableFuture.submit(
          () -> this.groupsClient.getMembership(membershipId),
          this.executor))
        .toList();

      var memberships = new HashSet<ActiveEntitlement>();
      for (var future : membershipFutures) {
        try {
          var membership = ThrowingCompletableFuture.awaitAndRethrow(future);

          //
          // NB. Temporary group memberships don't have a start date.
          //
          memberships.add(new ActiveEntitlement(
            new GroupMembership(new GroupEmail(membership.getPreferredMemberKey().getId())),
            new TimeSpan(
              Instant.EPOCH,
              membership.getRoles()
                .stream()
                .filter(r -> r.getExpiryDetail() != null && r.getExpiryDetail().getExpireTime() != null)
                .map(d -> Instant.parse(d.getExpiryDetail().getExpireTime()))
                .min(Instant::compareTo)
                .orElse(null))));
        }
        catch (NotFoundException ignored) {
          //
          // Membership expired in the meantime.
          //
        }
      }

      return new User(user, memberships);
    }
  }
}
