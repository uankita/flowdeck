package com.flowdeck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowdeck.domain.User;
import com.flowdeck.domain.Workspace;
import com.flowdeck.domain.WorkspaceMember;
import com.flowdeck.domain.WorkspaceRole;
import com.flowdeck.repository.BoardRepository;
import com.flowdeck.repository.UserRepository;
import com.flowdeck.repository.WorkspaceMemberRepository;
import com.flowdeck.repository.WorkspaceRepository;
import com.flowdeck.security.AccessTokenService;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full context against a throwaway Postgres 16 and exercises the
 * board API end to end: Flyway migrates, Hibernate validates the schema it
 * produced, and MapStruct projects the result.
 *
 * <p>Redis is not required here: board endpoints only need a <em>verified</em>
 * access token, and {@link AccessTokenService} verifies those without any
 * Redis lookup (only refresh-token operations touch Redis — see
 * {@code AuthApiIntegrationTest}, which does start a Redis container).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class BoardApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private DataSource dataSource;
    @Autowired private BoardRepository boardRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private AccessTokenService accessTokenService;

    private UUID workspaceId;
    /** An OWNER of {@link #workspaceId} — the token most tests authenticate as. */
    private String ownerToken;

    @BeforeEach
    void createWorkspaceWithOwner() {
        Workspace workspace = new Workspace();
        workspace.setSlug("acme-" + UUID.randomUUID());
        workspace.setName("Acme Corp");
        workspaceId = workspaceRepository.save(workspace).getId();

        User owner = persistUser("owner-" + UUID.randomUUID() + "@example.com");
        addMember(workspace, owner, WorkspaceRole.OWNER);
        ownerToken = accessTokenService.generate(owner);
    }

    /**
     * The container is shared by the whole class, so tests must not leak
     * rows. Deleting the workspace cascades to boards/lists/cards/members via
     * {@code ON DELETE CASCADE} in the migration; users aren't referenced
     * from the workspace side of that cascade, so they're cleared separately.
     */
    @AfterEach
    void clearFixtures() {
        workspaceRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Test User");
        user.setPasswordHash("unused-in-this-test-class"); // never checked; tokens are minted directly
        return userRepository.save(user);
    }

    private void addMember(Workspace workspace, User user, WorkspaceRole role) {
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspace(workspace);
        member.setUser(user);
        member.setRole(role);
        workspaceMemberRepository.save(member);
    }

    private String boardsUrl() {
        return "/api/v1/workspaces/" + workspaceId + "/boards";
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken);
    }

    @Test
    void contextLoadsAndConnectsToPostgres() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isValid(5)).isTrue();
            assertThat(connection.getMetaData().getDatabaseProductName())
                    .isEqualTo("PostgreSQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
        }
    }

    @Test
    void flywayCreatedTheBoardSchema() {
        // Hibernate runs with ddl-auto=validate, so a successful context start
        // already proves the mapping matches. This asserts the table is usable.
        assertThat(boardRepository.count()).isZero();
    }

    @Test
    void createsBoardSeededWithDefaultLists() throws Exception {
        String payload =
                """
                {
                  "boardKey": "FLOW",
                  "name": "Flowdeck roadmap",
                  "description": "Q4 delivery"
                }
                """;

        mockMvc.perform(authed(post(boardsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.boardKey").value("FLOW"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.lists.length()").value(3))
                .andExpect(jsonPath("$.lists[0].name").value("Backlog"))
                .andExpect(jsonPath("$.lists[2].name").value("Done"));

        mockMvc.perform(authed(get(boardsUrl() + "/FLOW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Flowdeck roadmap"));
    }

    @Test
    void seededListRanksSortInCreationOrder() throws Exception {
        mockMvc.perform(authed(post(boardsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boardKey": "RANK", "name": "Rank check"}
                                """)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(authed(get(boardsUrl() + "/RANK")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Cheap check that avoids pulling in a JSON path library just for
        // this: the three list names appear in rank order in the raw JSON
        // array, which is only true if the ranks themselves sort correctly.
        int backlogIndex = body.indexOf("Backlog");
        int inProgressIndex = body.indexOf("In progress");
        int doneIndex = body.indexOf("Done");
        assertThat(backlogIndex).isLessThan(inProgressIndex);
        assertThat(inProgressIndex).isLessThan(doneIndex);
    }

    @Test
    void rejectsDuplicateBoardKeyWithinTheSameWorkspace() throws Exception {
        String payload = """
                {"boardKey": "DUP", "name": "First"}
                """;

        mockMvc.perform(authed(post(boardsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(post(boardsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)))
                .andExpect(status().isConflict());
    }

    @Test
    void sameBoardKeyIsAllowedInADifferentWorkspace() throws Exception {
        // Demonstrates that boards.board_key uniqueness (uq_boards_workspace_key
        // in the migration) is scoped per-workspace, not global.
        String payload = """
                {"boardKey": "SHARED", "name": "First workspace's board"}
                """;
        mockMvc.perform(authed(post(boardsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)))
                .andExpect(status().isCreated());

        Workspace other = new Workspace();
        other.setSlug("other-" + UUID.randomUUID());
        other.setName("Other Co");
        other = workspaceRepository.save(other);
        User otherOwner = persistUser("other-owner-" + UUID.randomUUID() + "@example.com");
        addMember(other, otherOwner, WorkspaceRole.OWNER);
        String otherOwnerToken = accessTokenService.generate(otherOwner);

        mockMvc.perform(post("/api/v1/workspaces/" + other.getId() + "/boards")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boardKey": "SHARED", "name": "Second workspace's board"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsMalformedBoardKey() throws Exception {
        mockMvc.perform(authed(post(boardsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boardKey": "lower case", "name": "Nope"}
                                """)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns403ForAnUnknownWorkspaceRatherThanLeakingWhetherItExists() throws Exception {
        // The caller isn't a member of a workspace that doesn't exist, so
        // @PreAuthorize denies the request before BoardService ever runs —
        // same as any other non-member request. Returning 404 here instead
        // would let a caller distinguish "no such workspace" from "exists,
        // but you can't see it", which the API deliberately doesn't reveal.
        mockMvc.perform(authed(get("/api/v1/workspaces/" + UUID.randomUUID() + "/boards/ANY")))
                .andExpect(status().isForbidden());
    }

    @Test
    void returns404ForAnUnknownBoardInAWorkspaceTheCallerCanSee() throws Exception {
        mockMvc.perform(authed(get(boardsUrl() + "/NOPE"))).andExpect(status().isNotFound());
    }

    @Test
    void rejectsRequestsWithNoAccessToken() throws Exception {
        mockMvc.perform(get(boardsUrl())).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsNonMembersOfTheWorkspace() throws Exception {
        User outsider = persistUser("outsider-" + UUID.randomUUID() + "@example.com");
        String outsiderToken = accessTokenService.generate(outsider);

        mockMvc.perform(get(boardsUrl()).header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerRoleCanReadButNotCreateBoards() throws Exception {
        User viewer = persistUser("viewer-" + UUID.randomUUID() + "@example.com");
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        addMember(workspace, viewer, WorkspaceRole.VIEWER);
        String viewerToken = accessTokenService.generate(viewer);

        mockMvc.perform(get(boardsUrl()).header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post(boardsUrl())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boardKey": "NOPE", "name": "Should be forbidden"}
                                """))
                .andExpect(status().isForbidden());
    }
}
