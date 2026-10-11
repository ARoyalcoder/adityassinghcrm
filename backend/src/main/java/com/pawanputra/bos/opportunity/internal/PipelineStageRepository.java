package com.pawanputra.bos.opportunity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineStageRepository extends JpaRepository<PipelineStage, UUID> {

    List<PipelineStage> findByPipelineIdOrderByDisplayOrderAsc(UUID pipelineId);

    Optional<PipelineStage> findByIdAndPipelineId(UUID id, UUID pipelineId);
}
