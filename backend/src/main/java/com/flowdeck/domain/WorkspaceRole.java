package com.flowdeck.domain;

/**
 * A user's permission level within one {@link Workspace}, held via
 * {@link WorkspaceMember}. Ordered loosely from most to least privileged;
 * nothing currently depends on ordinal order (persisted as {@code STRING}).
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
