package com.google.solutions.jitaccess.core.catalog.project;

import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilege;
import com.google.solutions.jitaccess.core.catalog.RequesterPrivilegeSet;
import com.google.solutions.jitaccess.core.catalog.ActivationType;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.SortedSet;

/**
 * Repository for ProjectRoleBinding-based privileges.
 */
public interface ProjectRoleRepository {

    /**
     * Find projects that a user has standing, requester privileges in.
     */
    SortedSet<ProjectId> findProjectsWithRequesterPrivileges(
            UserId user) throws AccessException, IOException;

    /**
     * List requester privileges for the given user.
     */
    RequesterPrivilegeSet<ProjectRoleBinding> findRequesterPrivileges(
            UserId user,
            ProjectId projectId,
            Set<ActivationType> typesToInclude,
            EnumSet<RequesterPrivilege.Status> statusesToInclude) throws AccessException, IOException;

    /**
     * List users that hold an eligible reviewer privilege for a role binding.
     */
    Set<UserId> findReviewerPrivelegeHolders(
            ProjectRoleBinding roleBinding,
            ActivationType activationType) throws AccessException, IOException;
}
