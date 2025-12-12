package com.shutu.model.vo.ws.request;

import lombok.Data;

@Data
public class AIInterviewReq {
    /**
     * Action type: START, AUDIO_CHUNK, VAD_START, VAD_END, INTERRUPT
     */
    private String action;

    /**
     * Payload data (e.g., base64 audio, or JSON params)
     */
    private String payload;

    /**
     * Interview Session ID
     */
    private String interviewId;
}
