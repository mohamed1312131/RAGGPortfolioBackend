package com.example.chatml.controller;

import com.example.chatml.model.ChatRequest;
import com.example.chatml.service.RagChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRagController {

    private final RagChatService ragChatService;

    @PostMapping("/query")
    public String query(@RequestBody ChatRequest chatRequest) {
        // Pass the entire history to the service
        return ragChatService.answer(chatRequest.getHistory());
    }
}
