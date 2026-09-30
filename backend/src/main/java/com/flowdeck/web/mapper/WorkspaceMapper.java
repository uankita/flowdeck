package com.flowdeck.web.mapper;

import com.flowdeck.domain.Workspace;
import com.flowdeck.web.dto.WorkspaceDtos.WorkspaceResponse;
import org.mapstruct.Mapper;

@Mapper
public interface WorkspaceMapper {

    WorkspaceResponse toResponse(Workspace workspace);
}
