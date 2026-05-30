package org.interview.chat;

import org.interview.chat.dto.ChatRequest;
import org.interview.chat.dto.ChatResponse;
import org.interview.chat.dto.MessageAnalysis;
import org.interview.chat.memory.JpaChatMemoryRepository;
import org.interview.common.ai.StructuredOutputService;
import org.interview.common.result.Result;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ChatController {

    private final ChatClient.Builder builder;
    private final MessageChatMemoryAdvisor chatMemoryAdvisor;
    private final StructuredOutputService structuredOutputService;

    public ChatController(ChatClient.Builder builder, StructuredOutputService structuredOutputService,
                          JpaChatMemoryRepository jpaChatMemoryRepository) {
        this.builder = builder;
        this.structuredOutputService = structuredOutputService;
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jpaChatMemoryRepository)
                .maxMessages(20)
                .build();
        this.chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @PostMapping("/api/chat")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        String sid = sessionId;

        // 多轮对话：每次新建 ChatClient + advisor，避免共享 Builder 副作用
        ChatClient chatClient = builder.build();
        String reply = chatClient.prompt()
                .user(request.message())
                .advisors(chatMemoryAdvisor)
                .advisors(a -> a.param("chat_memory_conversation_id", sid))
                .call()
                .content();

        return Result.success(new ChatResponse(sessionId, reply));
    }

    @PostMapping("/api/chat/analyze")
    public Result<MessageAnalysis> analyze(@RequestBody ChatRequest request) {
        BeanOutputConverter<MessageAnalysis> converter =
                new BeanOutputConverter<>(MessageAnalysis.class);

         String systemPrompt = """
            你是一个面试答案分析助手。分析用户的回答，按以下结构返回 JSON。

            %s
            """.formatted(converter.getFormat());

        MessageAnalysis result = structuredOutputService.invoke(
                builder.build(), systemPrompt, request.message(), converter, "答案分析");

        return Result.success(result);
    }
}
