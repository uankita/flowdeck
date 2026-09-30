-- Flowdeck initial schema.
--
-- Hierarchy: workspaces -> boards -> board_lists -> cards, with users
-- attached to workspaces (via workspace_members), cards (via
-- card_assignees), and comments/activity as authors/actors.
--
-- Hibernate runs with ddl-auto=validate (see application.yml), so every
-- column here must match the entity mappings in com.flowdeck.domain exactly:
-- nullability, length, and type all have to agree or the app refuses to boot.
--
-- Primary keys are UUID everywhere, generated application-side by Hibernate
-- (GenerationType.UUID on BaseEntity) rather than a DB-side default — so no
-- column here needs `DEFAULT gen_random_uuid()`.

-- ============================================================================
-- users
-- ============================================================================

CREATE TABLE users (
    id            UUID          PRIMARY KEY,
    email         VARCHAR(320)  NOT NULL,  -- 320 = max valid email length per RFC 5321/5322
    display_name  VARCHAR(120)  NOT NULL,
    password_hash VARCHAR(100)  NOT NULL,  -- bcrypt output is 60 chars; headroom for a future algorithm
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    -- Login is always "look up by email" — the unique constraint doubles as
    -- that lookup's index, so no separate index is needed.
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ============================================================================
-- workspaces
-- ============================================================================

CREATE TABLE workspaces (
    id         UUID         PRIMARY KEY,
    slug       VARCHAR(60)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    -- Workspaces are addressed by slug in URLs; unique constraint = lookup index.
    CONSTRAINT uq_workspaces_slug UNIQUE (slug)
);

-- ============================================================================
-- workspace_members  (User <-> Workspace, with a role)
-- ============================================================================

CREATE TABLE workspace_members (
    id           UUID        PRIMARY KEY,
    workspace_id UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    role         VARCHAR(16) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workspace_members_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_members_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_workspace_members_role
        CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')),
    -- One membership row per (workspace, user): both a correctness constraint
    -- (can't join twice) and, as the leading column, the index that powers
    -- "list this workspace's members" — no separate workspace_id index needed.
    CONSTRAINT uq_workspace_members_workspace_user UNIQUE (workspace_id, user_id)
);

-- workspace_id is covered as the leading column of the unique constraint
-- above. user_id is NOT — it's the trailing column — so "list this user's
-- workspaces" (the reverse direction) needs its own index.
CREATE INDEX idx_workspace_members_user ON workspace_members (user_id);

-- ============================================================================
-- boards
-- ============================================================================

CREATE TABLE boards (
    id           UUID          PRIMARY KEY,
    workspace_id UUID          NOT NULL,
    board_key    VARCHAR(16)   NOT NULL,
    name         VARCHAR(200)  NOT NULL,
    description  VARCHAR(2000),
    archived     BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMPTZ,  -- soft delete: NULL = live. No @Version — boards
                                -- aren't drag-and-drop-concurrent the way lists/cards are.
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_boards_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE
);

-- Board key (e.g. "FLOW") is only required to be unique within its workspace,
-- not globally, and only among LIVE boards: a partial unique index lets a
-- deleted board's key be reused. This index also covers plain "list this
-- workspace's boards" queries as its leading column, so no separate
-- workspace_id index is added.
CREATE UNIQUE INDEX uq_boards_workspace_key
    ON boards (workspace_id, board_key)
    WHERE deleted_at IS NULL;

-- Small, targeted index for a periodic "purge boards soft-deleted long ago"
-- job. Partial on IS NOT NULL keeps it tiny, since most boards are live.
CREATE INDEX idx_boards_deleted_at ON boards (deleted_at) WHERE deleted_at IS NOT NULL;

-- ============================================================================
-- board_lists
-- ============================================================================

CREATE TABLE board_lists (
    id         UUID             PRIMARY KEY,
    board_id   UUID             NOT NULL,
    name       VARCHAR(120)     NOT NULL,
    rank       VARCHAR(255)     NOT NULL,  -- lexicographic rank string; see cards.rank below
    wip_limit  INTEGER,
    version    BIGINT           NOT NULL DEFAULT 0,  -- @Version: concurrent drags touch list state
    created_at TIMESTAMPTZ      NOT NULL,
    updated_at TIMESTAMPTZ      NOT NULL,
    CONSTRAINT fk_board_lists_board
        FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE,
    CONSTRAINT ck_board_lists_wip_limit
        CHECK (wip_limit IS NULL OR wip_limit > 0),
    -- Two lists on the same board can never share a rank: that would make
    -- their relative order ambiguous. This also means the DB itself rejects a
    -- buggy/racing rank computation instead of silently corrupting order.
    -- As the leading column, board_id-only lookups are covered too.
    CONSTRAINT uq_board_lists_board_rank UNIQUE (board_id, rank)
);

-- ============================================================================
-- cards
-- ============================================================================

CREATE TABLE cards (
    id            UUID             PRIMARY KEY,
    board_list_id UUID             NOT NULL,
    title         VARCHAR(300)     NOT NULL,
    description   VARCHAR(8000),
    -- Lexicographic rank string (base36, generated by
    -- com.flowdeck.util.RankGenerator), NOT an integer position. Moving a
    -- card writes only that card's row with a rank computed to sit between
    -- its new neighbours; an integer position would require renumbering every
    -- following card on every move. `ORDER BY rank` then gives display order
    -- directly, no separate position/sort pass needed.
    rank          VARCHAR(255)     NOT NULL,
    priority      VARCHAR(16)      NOT NULL DEFAULT 'MEDIUM',
    due_at        TIMESTAMPTZ,
    deleted_at    TIMESTAMPTZ,  -- soft delete: keeps comments/activity/assignees valid after "deletion"
    version       BIGINT           NOT NULL DEFAULT 0,  -- @Version: concurrent move/edit races
    created_at    TIMESTAMPTZ      NOT NULL,
    updated_at    TIMESTAMPTZ      NOT NULL,
    CONSTRAINT fk_cards_board_list
        FOREIGN KEY (board_list_id) REFERENCES board_lists (id) ON DELETE CASCADE,
    CONSTRAINT ck_cards_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

-- THE hot-path index for this whole schema: rendering a board means, for each
-- list, "give me its live cards in rank order" — this index answers that
-- directly via a sorted range scan, with no separate sort step. It's UNIQUE
-- (a list's cards must have an unambiguous order — same reasoning as
-- board_lists.rank above) and PARTIAL on deleted_at IS NULL so:
--   (a) a soft-deleted card's stale rank never collides with a live card's,
--       and never needs to be touched on delete;
--   (b) the index only ever contains rows the hot query actually wants,
--       keeping it as small (and cache-friendly) as possible.
-- board_list_id-only lookups (e.g. "does this list have any cards") are
-- covered by this index's leading column, so a separate index on just
-- board_list_id would be redundant and is intentionally omitted.
CREATE UNIQUE INDEX uq_cards_board_list_rank
    ON cards (board_list_id, rank)
    WHERE deleted_at IS NULL;

-- Same purge-job rationale as idx_boards_deleted_at.
CREATE INDEX idx_cards_deleted_at ON cards (deleted_at) WHERE deleted_at IS NOT NULL;

-- ============================================================================
-- card_assignees  (User <-> Card, many-to-many)
-- ============================================================================

CREATE TABLE card_assignees (
    id         UUID        PRIMARY KEY,
    card_id    UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_card_assignees_card
        FOREIGN KEY (card_id) REFERENCES cards (id) ON DELETE CASCADE,
    CONSTRAINT fk_card_assignees_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- Prevents assigning the same person to a card twice. Leading column
    -- covers "who is assigned to this card" (the card view's own need), so no
    -- separate card_id index is added.
    CONSTRAINT uq_card_assignees_card_user UNIQUE (card_id, user_id)
);

-- user_id is the trailing column above, so it needs its own index to serve
-- the reverse direction: "my assigned cards" (a per-user dashboard query).
CREATE INDEX idx_card_assignees_user ON card_assignees (user_id);

-- ============================================================================
-- labels
-- ============================================================================

CREATE TABLE labels (
    id         UUID        PRIMARY KEY,
    board_id   UUID        NOT NULL,
    name       VARCHAR(60) NOT NULL,
    color      VARCHAR(7)  NOT NULL,  -- '#rrggbb'
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_labels_board
        FOREIGN KEY (board_id) REFERENCES boards (id) ON DELETE CASCADE,
    -- No two labels on the same board share a name. Also the index for
    -- "list this board's labels" (the label picker), via its leading column —
    -- no separate board_id index needed.
    CONSTRAINT uq_labels_board_name UNIQUE (board_id, name)
);

-- ============================================================================
-- card_labels  (Card <-> Label, many-to-many)
-- ============================================================================

CREATE TABLE card_labels (
    id         UUID        PRIMARY KEY,
    card_id    UUID        NOT NULL,
    label_id   UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_card_labels_card
        FOREIGN KEY (card_id) REFERENCES cards (id) ON DELETE CASCADE,
    CONSTRAINT fk_card_labels_label
        FOREIGN KEY (label_id) REFERENCES labels (id) ON DELETE CASCADE,
    -- Prevents applying the same label twice; leading column covers "labels
    -- on this card" (rendering a card's chips), so no separate card_id index.
    CONSTRAINT uq_card_labels_card_label UNIQUE (card_id, label_id)
);

-- label_id is the trailing column above; its own index serves the reverse
-- direction: "all cards with this label" (filtering a board by label).
CREATE INDEX idx_card_labels_label ON card_labels (label_id);

-- ============================================================================
-- comments
-- ============================================================================

CREATE TABLE comments (
    id         UUID          PRIMARY KEY,
    card_id    UUID          NOT NULL,
    author_id  UUID          NOT NULL,
    body       VARCHAR(10000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,  -- doubles as "last edited at"
    CONSTRAINT fk_comments_card
        FOREIGN KEY (card_id) REFERENCES cards (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author
        FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Composite, not just (card_id): a card's comment thread is always rendered
-- oldest/newest first, so baking created_at into the index avoids a separate
-- sort step. Leading column still covers plain card_id lookups.
CREATE INDEX idx_comments_card_created ON comments (card_id, created_at);

-- Reverse direction: "comments by this user" (profile/moderation view).
CREATE INDEX idx_comments_author ON comments (author_id);

-- ============================================================================
-- activity_events
-- ============================================================================

CREATE TABLE activity_events (
    id           UUID        PRIMARY KEY,
    workspace_id UUID        NOT NULL,
    -- Deliberately plain UUID columns, not foreign keys to boards/cards:
    -- both of those tables are soft-deletable, and this is a permanent audit
    -- log that must still show "card X was deleted" after X is gone. A FK
    -- with ON DELETE CASCADE would erase the history along with the card;
    -- ON DELETE SET NULL would erase which card it was about. Neither is
    -- acceptable for an audit trail, so integrity here is the app's job, not
    -- the schema's.
    board_id     UUID,
    card_id      UUID,
    actor_id     UUID,        -- nullable: system-generated events have no human actor
    event_type   VARCHAR(40)  NOT NULL,
    metadata     VARCHAR(4000),
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_activity_events_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    -- actor_id IS a real FK (users are never soft- or hard-deleted from under
    -- an event in normal operation); ON DELETE SET NULL preserves the event
    -- ("someone did this") if a user account is ever actually removed.
    CONSTRAINT fk_activity_events_actor
        FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
);

-- The three feeds this table exists to serve, each wanting "latest N, this
-- scope" — composite indexes with created_at DESC as the trailing column let
-- each read as an index range scan in the exact order the UI wants, no sort:
CREATE INDEX idx_activity_events_workspace_created ON activity_events (workspace_id, created_at DESC);
CREATE INDEX idx_activity_events_board_created ON activity_events (board_id, created_at DESC) WHERE board_id IS NOT NULL;
CREATE INDEX idx_activity_events_card_created ON activity_events (card_id, created_at DESC) WHERE card_id IS NOT NULL;

-- Reverse direction: "did this user do X" / per-actor audit queries.
CREATE INDEX idx_activity_events_actor ON activity_events (actor_id) WHERE actor_id IS NOT NULL;
