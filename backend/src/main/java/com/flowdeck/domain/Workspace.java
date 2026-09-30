package com.flowdeck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The top-level tenant boundary: a company or team. Owns zero or more
 * {@link Board}s; membership and permissions are tracked per-user via
 * {@link WorkspaceMember} rather than a direct owner column, so ownership can
 * be transferred by simply changing a role.
 */
@Entity
@Table(name = "workspaces")
@Getter
@Setter
@NoArgsConstructor
public class Workspace extends BaseEntity {

    /** URL-safe identifier, e.g. for {@code /w/acme-corp}. */
    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;
}
