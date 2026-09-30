package com.flowdeck.web.mapper;

import com.flowdeck.domain.Label;
import com.flowdeck.web.dto.LabelDtos.LabelResponse;
import org.mapstruct.Mapper;

@Mapper
public interface LabelMapper {

    LabelResponse toResponse(Label label);
}
