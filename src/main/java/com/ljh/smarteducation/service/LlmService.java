package com.ljh.smarteducation.service;

import reactor.core.publisher.Mono;

public interface LlmService {
    Mono<String> getStructuredQuestions(String rawText, String subject);
}