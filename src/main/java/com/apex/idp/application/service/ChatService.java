package com.apex.idp.application.service;

import com.apex.idp.interfaces.dto.ChatRequestDTO;
import com.apex.idp.interfaces.dto.ChatResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final AnalysisService analysisService;

    public ChatResponseDTO chat(String batchId, ChatRequestDTO request) {
        return analysisService.chat(batchId, request);
    }
}