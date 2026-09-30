package com.flowdeck.web.mapper;

import com.flowdeck.domain.Board;
import com.flowdeck.domain.BoardList;
import com.flowdeck.domain.Card;
import com.flowdeck.web.dto.BoardDtos.BoardDetailResponse;
import com.flowdeck.web.dto.BoardDtos.BoardListResponse;
import com.flowdeck.web.dto.BoardDtos.BoardSummaryResponse;
import com.flowdeck.web.dto.BoardDtos.CardResponse;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * Entity → DTO projection.
 *
 * <p>The component model is set to {@code spring} globally via a compiler arg
 * in pom.xml, so the generated implementation is a {@code @Component}.
 *
 * <p>Mapping is one-directional on purpose: inbound requests are turned into
 * entities by the service, where invariants (key uniqueness, default lists)
 * belong.
 */
@Mapper
public interface BoardMapper {

    BoardSummaryResponse toSummary(Board board);

    BoardDetailResponse toDetail(Board board);

    List<BoardSummaryResponse> toSummaries(List<Board> boards);

    BoardListResponse toListResponse(BoardList list);

    CardResponse toCardResponse(Card card);
}
