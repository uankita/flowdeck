package com.flowdeck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowdeck.domain.Workspace;
import com.flowdeck.repository.BoardRepository;
import com.flowdeck.repository.WorkspaceRepository;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full context against a throwaway Postgres 16 and exercises the
 * board API end to end: Flyway migrates, Hibernate validates the schema it
 * produced, and MapStruct projects the result.
 *
 * <p>Redis is not required here — the context only opens a Redis connection
 * lazily, so no container is started for it.
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

    private UUID workspaceId;

    /** Boards require a workspace (FK not null), so every test gets a fresh one. */
    @BeforeEach
    void createWorkspace() {
        Workspace workspace = new Workspace();
        workspace.setSlug("acme-" + UUID.randomUUID());
        workspace.setName("Acme Corp");
        workspaceId = workspaceRepository.save(workspace).getId();
    }

    /**
     * The container is shared by the whole class, so tests must not leak
     * rows. Deleting the workspace is enough: every child table cascades from
     * it via {@code ON DELETE CASCADE} in the migration.
     */
    @AfterEach
    void clearWorkspaces() {
        workspaceRepository.deleteAll();
    }

    private String boardsUrl() {
        return "/api/v1/workspaces/" + workspaceId + "/boards";
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

        mockMvc.perform(post(boardsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.boardKey").value("FLOW"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.lists.length()").value(3))
                .andExpect(jsonPath("$.lists[0].name").value("Backlog"))
                .andExpect(jsonPath("$.lists[2].name").value("Done"));

        mockMvc.perform(get(boardsUrl() + "/FLOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Flowdeck roadmap"));
    }

    @Test
    void seededListRanksSortInCreationOrder() throws Exception {
        mockMvc.perform(post(boardsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boardKey": "RANK", "name": "Rank check"}
                                """))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get(boardsUrl() + "/RANK"))
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

        mockMvc.perform(post(boardsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post(boardsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void sameBoardKeyIsAllowedInADifferentWorkspace() throws Exception {
        // Demonstrates that boards.board_key uniqueness (uq_boards_workspace_key
        // in the migration) is scoped per-workspace, not global.
        String payload = """
                {"boardKey": "SHARED", "name": "First workspace's board"}
                """;
        mockMvc.perform(post(boardsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        Workspace other = new Workspace();
        other.setSlug("other-" + UUID.randomUUID());
        other.setName("Other Co");
        UUID otherWorkspaceId = workspaceRepository.save(other).getId();

        mockMvc.perform(post("/api/v1/workspaces/" + otherWorkspaceId + "/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boardKey": "SHARED", "name": "Second workspace's board"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsMalformedBoardKey() throws Exception {
        mockMvc.perform(post(boardsUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boardKey": "lower case", "name": "Nope"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404ForUnknownWorkspace() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/" + UUID.randomUUID() + "/boards/ANY"))
                .andExpect(status().isNotFound());
    }
}
