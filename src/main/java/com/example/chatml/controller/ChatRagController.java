package com.example.chatml.controller;

import com.example.chatml.service.RagChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRagController {

    private final RagChatService ragChatService;

    @PostMapping("/query")
    public String query(@RequestBody String question) {
        return ragChatService.answer(question);
    }
}
