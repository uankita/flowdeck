package com.flowdeck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowdeck.domain.Board;
import com.flowdeck.domain.BoardList;
import com.flowdeck.domain.Card;
import com.flowdeck.domain.User;
import com.flowdeck.domain.Workspace;
import com.flowdeck.domain.WorkspaceMember;
import com.flowdeck.domain.WorkspaceRole;
import com.flowdeck.repository.BoardListRepository;
import com.flowdeck.repository.BoardRepository;
import com.flowdeck.repository.CardRepository;
import com.flowdeck.repository.UserRepository;
import com.flowdeck.repository.WorkspaceMemberRepository;
import com.flowdeck.repository.WorkspaceRepository;
import com.flowdeck.security.AccessTokenService;
import com.flowdeck.util.RankExhaustionException;
import com.flowdeck.util.RankGenerator;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises {@code CardService#moveCard} through the real HTTP + Postgres
 * stack: {@code PATCH /api/v1/workspaces/{workspaceId}/cards/{cardId}/move}.
 *
 * <p>{@code RankGeneratorTest} already thoroughly covers the rank algorithm
 * itself in isolation; this class's job is proving the service wires it up
 * correctly against real persistence — one-row writes, cross-list moves,
 * workspace-role authorization, and the exhaustion → rebalance → retry path
 * actually committing together in one transaction.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CardMoveIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private BoardRepository boardRepository;
    @Autowired private BoardListRepository boardListRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private AccessTokenService accessTokenService;

    private UUID workspaceId;
    private UUID listId;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        Workspace workspace = new Workspace();
        workspace.setSlug("cm-" + UUID.randomUUID());
        workspace.setName("Card Move Co");
        workspace = workspaceRepository.save(workspace);
        workspaceId = workspace.getId();

        User owner = persistUser("owner-" + UUID.randomUUID() + "@example.com");
        addMember(workspace, owner, WorkspaceRole.OWNER);
        ownerToken = accessTokenService.generate(owner);

        Board board = new Board();
        board.setWorkspace(workspace);
        board.setBoardKey("CM");
        board.setName("Card move board");
        board = boardRepository.save(board);

        BoardList list = new BoardList();
        list.setBoard(board);
        list.setName("Todo");
        list.setRank(RankGenerator.initial());
        listId = boardListRepository.save(list).getId();
    }

    @AfterEach
    void cleanUp() {
        // Cascades boards/lists/cards/members via ON DELETE CASCADE.
        workspaceRepository.deleteAll();
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

    private BoardList fetchList(UUID id) {
        return boardListRepository.findById(id).orElseThrow();
    }

    private Card persistCard(UUID targetListId, String title, String rank) {
        Card card = new Card();
        card.setList(fetchList(targetListId));
        card.setTitle(title);
        card.setRank(rank);
        return cardRepository.save(card);
    }

    /** A second list on the same board as the fixture list, for cross-list tests. */
    private UUID persistAnotherList(String name) {
        BoardList existing = fetchList(listId);
        BoardList other = new BoardList();
        other.setBoard(existing.getBoard());
        other.setName(name);
        other.setRank(RankGenerator.between(existing.getRank(), null));
        return boardListRepository.save(other).getId();
    }

    private String moveUrl(UUID cardId) {
        return "/api/v1/workspaces/" + workspaceId + "/cards/" + cardId + "/move";
    }

    private ResultActions performMove(String token, UUID cardId, String body) throws Exception {
        return mockMvc.perform(
                patch(moveUrl(cardId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    // ── basic moves ─────────────────────────────────────────────────────

    @Test
    void movingACardWritesOnlyThatCardsRow() throws Exception {
        Card a = persistCard(listId, "A", "d");
        Card b = persistCard(listId, "B", "m");
        Card c = persistCard(listId, "C", "t");

        performMove(
                        ownerToken,
                        c.getId(),
                        """
                        {"targetListId": "%s", "previousCardId": "%s", "nextCardId": "%s"}
                        """
                                .formatted(listId, a.getId(), b.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(allOf(greaterThan(a.getRank()), lessThan(b.getRank()))));

        // A and B were never the target of a write — prove it via their
        // optimistic-lock version, which Hibernate only bumps on an actual
        // UPDATE of that row.
        Card aAfter = cardRepository.findById(a.getId()).orElseThrow();
        Card bAfter = cardRepository.findById(b.getId()).orElseThrow();
        assertThat(aAfter.getVersion()).isEqualTo(a.getVersion());
        assertThat(aAfter.getUpdatedAt()).isEqualTo(a.getUpdatedAt());
        assertThat(bAfter.getVersion()).isEqualTo(b.getVersion());
        assertThat(bAfter.getUpdatedAt()).isEqualTo(b.getUpdatedAt());

        Card cAfter = cardRepository.findById(c.getId()).orElseThrow();
        assertThat(cAfter.getVersion()).isGreaterThan(c.getVersion());
        assertThat(cAfter.getRank()).isGreaterThan(a.getRank()).isLessThan(b.getRank());
    }

    @Test
    void movingToTheStartUsesNoPreviousCard() throws Exception {
        Card first = persistCard(listId, "First", "m");
        Card mover = persistCard(listId, "Mover", "z");

        performMove(
                        ownerToken,
                        mover.getId(),
                        """
                        {"targetListId": "%s", "previousCardId": null, "nextCardId": "%s"}
                        """
                                .formatted(listId, first.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(lessThan(first.getRank())));
    }

    @Test
    void movingToTheEndUsesNoNextCard() throws Exception {
        Card last = persistCard(listId, "Last", "m");
        Card mover = persistCard(listId, "Mover", "a");

        performMove(
                        ownerToken,
                        mover.getId(),
                        """
                        {"targetListId": "%s", "previousCardId": "%s", "nextCardId": null}
                        """
                                .formatted(listId, last.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(greaterThan(last.getRank())));
    }

    @Test
    void movingToAnEmptyListUsesNoNeighboursAtAll() throws Exception {
        UUID otherListId = persistAnotherList("Empty list");
        Card mover = persistCard(listId, "Mover", "m");

        performMove(
                        ownerToken,
                        mover.getId(),
                        """
                        {"targetListId": "%s", "previousCardId": null, "nextCardId": null}
                        """
                                .formatted(otherListId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").isNotEmpty());

        Card moved = cardRepository.findById(mover.getId()).orElseThrow();
        assertThat(moved.getList().getId()).isEqualTo(otherListId);
    }

    // ── validation ──────────────────────────────────────────────────────

    @Test
    void rejectsANeighbourThatIsNotInTheTargetList() throws Exception {
        UUID otherListId = persistAnotherList("Other list");
        Card outsider = persistCard(otherListId, "Outsider", "m");
        Card mover = persistCard(listId, "Mover", "a");

        performMove(
                        ownerToken,
                        mover.getId(),
                        """
                        {"targetListId": "%s", "previousCardId": "%s", "nextCardId": null}
                        """
                                .formatted(listId, outsider.getId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsANeighbourThatIsTheCardBeingMoved() throws Exception {
        Card mover = persistCard(listId, "Mover", "m");

        performMove(
                        ownerToken,
                        mover.getId(),
                        """
                        {"targetListId": "%s", "previousCardId": "%s", "nextCardId": null}
                        """
                                .formatted(listId, mover.getId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404ForAnUnknownCard() throws Exception {
        performMove(
                        ownerToken,
                        UUID.randomUUID(),
                        """
                        {"targetListId": "%s"}
                        """
                                .formatted(listId))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns404ForAnUnknownTargetList() throws Exception {
        Card mover = persistCard(listId, "Mover", "m");
        performMove(
                        ownerToken,
                        mover.getId(),
                        """
                        {"targetListId": "%s"}
                        """
                                .formatted(UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── authorization ───────────────────────────────────────────────────

    @Test
    void rejectsAViewerTryingToMoveACard() throws Exception {
        User viewer = persistUser("viewer-" + UUID.randomUUID() + "@example.com");
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        addMember(workspace, viewer, WorkspaceRole.VIEWER);
        String viewerToken = accessTokenService.generate(viewer);

        Card mover = persistCard(listId, "Mover", "m");

        performMove(
                        viewerToken,
                        mover.getId(),
                        """
                        {"targetListId": "%s"}
                        """
                                .formatted(listId))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsACallerWhoIsNotAWorkspaceMemberAtAll() throws Exception {
        User outsider = persistUser("outsider-" + UUID.randomUUID() + "@example.com");
        String outsiderToken = accessTokenService.generate(outsider);

        Card mover = persistCard(listId, "Mover", "m");

        performMove(
                        outsiderToken,
                        mover.getId(),
                        """
                        {"targetListId": "%s"}
                        """
                                .formatted(listId))
                .andExpect(status().isForbidden());
    }

    // ── rank exhaustion ─────────────────────────────────────────────────

    @Test
    void movingIntoAnExhaustedGapTriggersARebalanceAndSucceeds() throws Exception {
        // Squeeze a gap down to exactly the point where one more between()
        // call would exceed RankGenerator.MAX_RANK_LENGTH — the same
        // technique RankGeneratorTest uses, just wired to real persisted
        // rows instead of bare strings.
        String left = "a";
        String right = "b";
        while (true) {
            String mid;
            try {
                mid = RankGenerator.between(left, right);
            } catch (RankExhaustionException e) {
                break;
            }
            right = mid;
        }
        // At this point RankGenerator.between(left, right) is guaranteed to
        // throw — persist left/right as two real, adjacent siblings.
        Card leftCard = persistCard(listId, "Left", left);
        Card rightCard = persistCard(listId, "Right", right);
        Card mover = persistCard(listId, "Mover", RankGenerator.between(right, null));

        performMove(
                        ownerToken,
                        mover.getId(),
                        """
                        {"targetListId": "%s", "previousCardId": "%s", "nextCardId": "%s"}
                        """
                                .formatted(listId, leftCard.getId(), rightCard.getId()))
                .andExpect(status().isOk());

        // The whole list was rebalanced to short ranks — and correctly
        // ordered, with the mover strictly between left and right.
        var afterMove = cardRepository.findByListIdOrderByRankAsc(listId);
        assertThat(afterMove)
                .as("every rank after rebalance")
                .allSatisfy(c -> assertThat(c.getRank().length()).isLessThanOrEqualTo(RankGenerator.MAX_RANK_LENGTH));

        Card leftAfter = cardRepository.findById(leftCard.getId()).orElseThrow();
        Card rightAfter = cardRepository.findById(rightCard.getId()).orElseThrow();
        Card moverAfter = cardRepository.findById(mover.getId()).orElseThrow();
        assertThat(moverAfter.getRank()).isGreaterThan(leftAfter.getRank()).isLessThan(rightAfter.getRank());
    }
}
