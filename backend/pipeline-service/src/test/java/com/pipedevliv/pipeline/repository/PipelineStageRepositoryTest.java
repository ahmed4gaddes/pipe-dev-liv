package com.pipedevliv.pipeline.repository;

import com.pipedevliv.pipeline.entity.PipelineStage;
import com.pipedevliv.pipeline.entity.PipelineStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PipelineStageRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PipelineStageRepository repository;

    @Test
    void findByExecutionIdOrderByStageOrderAsc_returnsInOrder() {
        entityManager.persistAndFlush(stage(1L, "test", 2));
        entityManager.persistAndFlush(stage(1L, "build", 1));
        entityManager.persistAndFlush(stage(2L, "other-execution", 1));

        List<PipelineStage> result = repository.findByExecutionIdOrderByStageOrderAsc(1L);

        assertThat(result).extracting(PipelineStage::getName).containsExactly("build", "test");
    }

    @Test
    void deleteByExecutionId_removesOnlyThatExecutionsStages() {
        entityManager.persistAndFlush(stage(1L, "build", 1));
        entityManager.persistAndFlush(stage(2L, "keep-me", 1));

        repository.deleteByExecutionId(1L);
        entityManager.flush();

        assertThat(repository.findByExecutionIdOrderByStageOrderAsc(1L)).isEmpty();
        assertThat(repository.findByExecutionIdOrderByStageOrderAsc(2L)).hasSize(1);
    }

    private PipelineStage stage(Long executionId, String name, int order) {
        return PipelineStage.builder()
                .executionId(executionId)
                .name(name)
                .status(PipelineStatus.SUCCESS)
                .stageOrder(order)
                .build();
    }
}
