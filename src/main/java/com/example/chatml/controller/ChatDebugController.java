package com.example.chatml.controller;

import com.example.chatml.model.AzureChatCompletion; // Import the new model
import com.example.chatml.model.ChatMessage;
import com.example.chatml.service.AzureChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class ChatDebugController {
    private final AzureChatClient azureChatClient;

    @PostMapping("/tone")
    public String testTone(@RequestBody String question) {
        var system = new ChatMessage(
                "system",
                // keep persona concise; we’ll refine later
                "You are Mohamed’s portfolio assistant. Be concise, professional, and ONLY answer using provided context when present. If unsure, say you’re not certain."
        );
        var user = new ChatMessage("user", question);

        // Call the client and get the full completion object
        AzureChatCompletion completion = azureChatClient.chatWithMessages(List.of(system, user));

        // Return just the content string, as this method requires
        return completion.getContent();
    }
}