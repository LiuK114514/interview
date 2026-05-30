package org.interview.chat.dto;

import java.util.List;

public record MessageAnalysis(
    String sentiment,
    int score,
    List<String> keywords,
    String suggestion
) {}
