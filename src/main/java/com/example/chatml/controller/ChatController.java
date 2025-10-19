package com.example.chatml.controller;



import com.example.chatml.service.AzureChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final AzureChatClient azureChatClient;

    @PostMapping
    public String chat(@RequestBody String question) {
        return azureChatClient.chat(question);
    }
}
