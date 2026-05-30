package org.interview.chat.dto;

public record ChatResponse(
    String sessionId,
    String reply
) {}