package com.flowdeck.domain;

/**
 * A user's permission level within one {@link Workspace}, held via
 * {@link WorkspaceMember}.
 *
 * <p>Declaration order is load-bearing: {@code com.flowdeck.security.WorkspaceAuthorization}
 * compares {@link #ordinal()} to answer "does this user have at least role
 * X" (most to least privileged, top to bottom). Persisted as {@code STRING}
 * in the database — reordering these constants is safe for the DB, but
 * changes what "at least" means, so don't do it without checking every
 * {@code hasAtLeastRole} call site.
 */
public enum WorkspaceRole {
    /** Full control, including deleting the workspace and transferring ownership. */
    OWNER,
    /** Can manage members, boards, and settings, but not delete the workspace. */
    ADMIN,
    /** Can create and edit boards, lists, and cards. */
    MEMBER,
    /** Read-only access. */
    VIEWER
}
