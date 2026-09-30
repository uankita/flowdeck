package com.flowdeck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdeck.domain.Board;
import com.flowdeck.domain.BoardList;
import com.flowdeck.domain.Card;
import com.flowdeck.domain.Label;
import com.flowdeck.domain.User;
import com.flowdeck.domain.Workspace;
import com.flowdeck.domain.WorkspaceMember;
import com.flowdeck.domain.WorkspaceRole;
import com.flowdeck.repository.BoardListRepository;
import com.flowdeck.repository.BoardRepository;
import com.flowdeck.repository.CardRepository;
import com.flowdeck.repository.LabelRepository;
import com.flowdeck.repository.UserRepository;
import com.flowdeck.repository.WorkspaceMemberRepository;
import com.flowdeck.repository.WorkspaceRepository;
import com.flowdeck.security.AccessTokenService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the card CRUD + label API end to end, against real Postgres. The
 * headline case is {@link #concurrentUpdatesRaceAndExactlyOneWins}: two real
 * threads, not a sequential simulation — see its Javadoc for why that's both
 * realistic and, despite being genuine concurrency, not flaky.
 *
 * <p>Card <em>move</em> semantics (rank placement, cross-list, rebalance-on-
 * exhaustion) are {@code CardMoveIntegrationTest}'s job, not this class's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CardApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private BoardRepository boardRepository;
    @Autowired private BoardListRepository boardListRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private LabelRepository labelRepository;
    @Autowired private AccessTokenService accessTokenService;

    private UUID workspaceId;
    private UUID boardId;
    private UUID listId;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        Workspace workspace = new Workspace();
        workspace.setSlug("card-api-" + UUID.randomUUID());
        workspace.setName("Card API Co");
        workspace = workspaceRepository.save(workspace);
        workspaceId = workspace.getId();

        User owner = persistUser("owner-" + UUID.randomUUID() + "@example.com");
        addMember(workspace, owner, WorkspaceRole.OWNER);
        ownerToken = accessTokenService.generate(owner);

        Board board = new Board();
        board.setWorkspace(workspace);
        board.setBoardKey("CA");
        board.setName("Card API board");
        board = boardRepository.save(board);
        boardId = board.getId();

        BoardList list = new BoardList();
        list.setBoard(board);
        list.setName("Todo");
        list.setRank("m");
        listId = boardListRepository.save(list).getId();
    }

    @AfterEach
    void cleanUp() {
        workspaceRepository.deleteAll(); // cascades boards/lists/cards/labels/members
        userRepository.deleteAll();
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Test User");
        user.setPasswordHash("unused-in-this-test-class");
        return userRepository.save(user);
    }

    private void addMember(Workspace workspace, User user, WorkspaceRole role) {
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspace(workspace);
        member.setUser(user);
        member.setRole(role);
        workspaceMemberRepository.save(member);
    }

    private Card persistCard(String title, String rank) {
        Card card = new Card();
        card.setList(boardListRepository.findById(listId).orElseThrow());
        card.setTitle(title);
        card.setRank(rank);
        return cardRepository.save(card);
    }

    private Label persistLabel(String name, String color) {
        Label label = new Label();
        label.setBoard(boardRepository.findById(boardId).orElseThrow());
        label.setName(name);
        label.setColor(color);
        return labelRepository.save(label);
    }

    private String cardsUrl() {
        return "/api/v1/workspaces/" + workspaceId + "/boards/CA/lists/" + listId + "/cards";
    }

    private String cardUrl(UUID cardId) {
        return "/api/v1/workspaces/" + workspaceId + "/cards/" + cardId;
    }

    private String cardLabelUrl(UUID cardId, UUID labelId) {
        return cardUrl(cardId) + "/labels/" + labelId;
    }

    private MvcResult performUpdate(UUID cardId, String title, long version) throws Exception {
        return mockMvc
                .perform(patch(cardUrl(cardId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "priority": "MEDIUM", "version": %d}
                                """.formatted(title, version)))
                .andReturn();
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ── create ──────────────────────────────────────────────────────────

    @Test
    void createCardSucceeds() throws Exception {
        mockMvc.perform(post(cardsUrl())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Ship the feature", "priority": "HIGH"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Ship the feature"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.rank").isNotEmpty());
    }

    @Test
    void createCardDefaultsPriorityToMedium() throws Exception {
        mockMvc.perform(post(cardsUrl())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "No priority given"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    void createCardRejectsABlankTitle() throws Exception {
        mockMvc.perform(post(cardsUrl())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"));
    }

    // ── get / list ──────────────────────────────────────────────────────

    @Test
    void getCardSucceeds() throws Exception {
        Card card = persistCard("Existing", "m");
        mockMvc.perform(get(cardUrl(card.getId())).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(card.getId().toString()))
                .andExpect(jsonPath("$.title").value("Existing"));
    }

    @Test
    void getCardReturns404ForAnUnknownId() throws Exception {
        mockMvc.perform(get(cardUrl(UUID.randomUUID())).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listCardsIsPaginated() throws Exception {
        persistCard("Card A", "a");
        persistCard("Card B", "b");
        persistCard("Card C", "c");

        mockMvc.perform(get(cardsUrl() + "?size=2&page=0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Card A"))
                .andExpect(jsonPath("$.content[1].title").value("Card B"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2));

        mockMvc.perform(get(cardsUrl() + "?size=2&page=1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Card C"));
    }

    // ── update ──────────────────────────────────────────────────────────

    @Test
    void updateCardSucceeds() throws Exception {
        Card card = persistCard("Before", "m");

        mockMvc.perform(patch(cardUrl(card.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "After", "description": "Now with detail", "priority": "URGENT", "version": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("After"))
                .andExpect(jsonPath("$.description").value("Now with detail"))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updateCardWithAMissingVersionFailsValidation() throws Exception {
        Card card = persistCard("Before", "m");
        mockMvc.perform(patch(cardUrl(card.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "After", "priority": "MEDIUM"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateWithAStaleVersionReturns409WithCurrentServerState() throws Exception {
        Card card = persistCard("Original", "m");

        // First edit: legitimate, version 0 -> 1.
        MvcResult firstEdit = performUpdate(card.getId(), "First edit", 0);
        assertThat(firstEdit.getResponse().getStatus()).isEqualTo(200);

        // Second edit: still carries version 0, as read before the first
        // edit — exactly what "two people had this card open at once, one
        // saved first" looks like from the second client's point of view.
        MvcResult result = performUpdate(card.getId(), "Second edit (stale)", 0);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);

        JsonNode body = bodyOf(result);
        assertThat(body.path("title").asText()).isEqualTo("Card was modified concurrently");
        JsonNode currentState = body.path("currentState");
        assertThat(currentState.path("id").asText()).isEqualTo(card.getId().toString());
        assertThat(currentState.path("title").asText()).isEqualTo("First edit");
        assertThat(currentState.path("version").asLong()).isEqualTo(1L);

        // The rejected edit never happened.
        Card afterConflict = cardRepository.findById(card.getId()).orElseThrow();
        assertThat(afterConflict.getTitle()).isEqualTo("First edit");
    }

    /**
     * The literal "concurrent-update test": two real threads, not two
     * sequential requests with a stale version. Both read the card's
     * starting version (0) before either writes — genuinely racing, exactly
     * as two people editing the same card around the same moment would.
     *
     * <p>This is deterministic despite being real concurrency, not
     * timing-dependent: Postgres only ever lets one {@code UPDATE ... WHERE
     * id = ? AND version = 0} succeed for a given row, no matter how the two
     * threads' statements happen to interleave. Whichever loses gets 409
     * with the winner's now-current state in the body — the same contract
     * {@link #updateWithAStaleVersionReturns409WithCurrentServerState}
     * checks deterministically, just reached here through an actual race
     * instead of a scripted one.
     */
    @Test
    void concurrentUpdatesRaceAndExactlyOneWins() throws Exception {
        Card card = persistCard("Original", "m");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> editA = () -> performUpdate(card.getId(), "Editor A", 0);
            Callable<MvcResult> editB = () -> performUpdate(card.getId(), "Editor B", 0);

            List<Future<MvcResult>> futures = executor.invokeAll(List.of(editA, editB));
            MvcResult resultA = futures.get(0).get();
            MvcResult resultB = futures.get(1).get();

            int statusA = resultA.getResponse().getStatus();
            int statusB = resultB.getResponse().getStatus();
            assertThat(List.of(statusA, statusB))
                    .as("exactly one editor wins, the other is rejected")
                    .containsExactlyInAnyOrder(200, 409);

            MvcResult winner = statusA == 200 ? resultA : resultB;
            MvcResult loser = statusA == 409 ? resultA : resultB;

            String winningTitle = bodyOf(winner).path("title").asText();
            assertThat(winningTitle).isIn("Editor A", "Editor B");

            // The loser's 409 body reflects the winner's write, not stale data.
            JsonNode loserCurrentState = bodyOf(loser).path("currentState");
            assertThat(loserCurrentState.path("title").asText()).isEqualTo(winningTitle);
            assertThat(loserCurrentState.path("version").asLong()).isEqualTo(1L);

            // The database agrees: exactly the winner's edit persisted.
            Card persisted = cardRepository.findById(card.getId()).orElseThrow();
            assertThat(persisted.getTitle()).isEqualTo(winningTitle);
            assertThat(persisted.getVersion()).isEqualTo(1L);
        } finally {
            executor.shutdown();
        }
    }

    // ── delete ──────────────────────────────────────────────────────────

    @Test
    void deleteCardSoftDeletesIt() throws Exception {
        Card card = persistCard("Doomed", "m");

        mockMvc.perform(delete(cardUrl(card.getId())).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // @SQLRestriction filters it out of every normal query from here on.
        mockMvc.perform(get(cardUrl(card.getId())).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
        assertThat(cardRepository.findById(card.getId())).isEmpty();
    }

    // ── labels ──────────────────────────────────────────────────────────

    @Test
    void attachListAndDetachALabel() throws Exception {
        Card card = persistCard("Card", "m");
        Label label = persistLabel("Bug", "#ff0000");

        mockMvc.perform(put(cardLabelUrl(card.getId(), label.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(cardUrl(card.getId()) + "/labels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Bug"))
                .andExpect(jsonPath("$[0].color").value("#ff0000"));

        // Attaching again is a no-op, not a duplicate or an error.
        mockMvc.perform(put(cardLabelUrl(card.getId(), label.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(cardUrl(card.getId()) + "/labels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete(cardLabelUrl(card.getId(), label.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(cardUrl(card.getId()) + "/labels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void detachingAnUnattachedLabelIsIdempotent() throws Exception {
        Card card = persistCard("Card", "m");
        Label label = persistLabel("Bug", "#ff0000");

        mockMvc.perform(delete(cardLabelUrl(card.getId(), label.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    // ── authorization ───────────────────────────────────────────────────

    @Test
    void mutatingWithNoTokenIsRejected() throws Exception {
        mockMvc.perform(post(cardsUrl()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": "Nope"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCannotUpdateACard() throws Exception {
        User viewer = persistUser("viewer-" + UUID.randomUUID() + "@example.com");
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        addMember(workspace, viewer, WorkspaceRole.VIEWER);
        String viewerToken = accessTokenService.generate(viewer);

        Card card = persistCard("Card", "m");

        mockMvc.perform(patch(cardUrl(card.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Should be forbidden", "priority": "MEDIUM", "version": 0}
                                """))
                .andExpect(status().isForbidden());
    }
}
