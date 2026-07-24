package com.pipedevliv.pipeline.exception;

import com.pipedevliv.common.exception.BusinessException;

public class GitHubApiException extends BusinessException {

    public GitHubApiException(String message) {
        super(message);
    }
}
