package com.pipedevliv.pipeline.entity;

/**
 * Version simplifiée du vocabulaire status/conclusion de l'API GitHub Actions
 * (status: queued/in_progress/completed ; conclusion: success/failure/cancelled/...).
 */
public enum PipelineStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}
