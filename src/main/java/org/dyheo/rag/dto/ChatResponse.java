package org.dyheo.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String answer;
    private List<SourceItem> sources;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceItem {
        private String text;
        private int tier; // 1: 매우 높음, 2: 높음, 3: 보통
    }
}
