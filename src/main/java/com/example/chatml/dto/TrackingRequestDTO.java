package com.example.chatml.dto;

import lombok.Data;
import java.util.Map;

@Data
public class TrackingRequestDTO {
    private String sessionId;
    private String question;
    private String timestamp;
    private String location;
    private String eventType; // page_view, question, link_click
    private Map<String, Object> metadata;
}
