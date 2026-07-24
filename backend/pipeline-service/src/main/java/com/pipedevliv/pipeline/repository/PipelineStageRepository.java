package com.pipedevliv.pipeline.repository;

import com.pipedevliv.pipeline.entity.PipelineStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineStageRepository extends JpaRepository<PipelineStage, Long> {

    List<PipelineStage> findByExecutionIdOrderByStageOrderAsc(Long executionId);

    void deleteByExecutionId(Long executionId);
}
