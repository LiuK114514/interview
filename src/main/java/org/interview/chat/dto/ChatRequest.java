package org.interview.chat.dto;

public record ChatRequest(
    String message,
    String sessionId
) {}
