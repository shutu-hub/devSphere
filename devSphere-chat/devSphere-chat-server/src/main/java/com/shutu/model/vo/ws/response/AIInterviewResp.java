package com.shutu.model.vo.ws.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AIInterviewResp {
    /**
     * Response type: TRANSCRIPT, AUDIO, VISEME, STATUS, TEXT
     */
    private String type;

    /**
     * Payload data
     */
    private String payload;
}
