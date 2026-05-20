package org.dyheo.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.dyheo.rag.dto.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagChatService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public ChatResponse generateAnswer(String question) {
        // 1. 유사도 검색 (VectorStore.similaritySearch)
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(5)
                .similarityThreshold(0.50f) // 한국어 임베딩 특성을 고려해 0.50으로 임계값 완화
                .build();
                
        List<Document> relevantDocuments = vectorStore.similaritySearch(searchRequest);

        // 검색된 문서 결과 로깅 추가
        log.info("========== [Similarity Search Results] ==========");
        log.info("Query: {}", question);
        log.info("Found {} relevant documents.", relevantDocuments.size());
        for (int i = 0; i < relevantDocuments.size(); i++) {
            Document doc = relevantDocuments.get(i);
            String file = (String) doc.getMetadata().getOrDefault("source_file", "Unknown");
            String page = String.valueOf(doc.getMetadata().getOrDefault("page_number", "?"));
            Object distance = doc.getMetadata().getOrDefault("distance", "N/A"); // 벡터 DB가 제공할 경우 출력
            
            String contentPreview = doc.getText().length() > 50 
                    ? doc.getText().substring(0, 50).replace("\n", " ") + "..." 
                    : doc.getText().replace("\n", " ");
                    
            log.info("[{}] File: {}, Page: {}, Distance: {}, Preview: {}", 
                    i + 1, file, page, distance, contentPreview);
        }
        log.info("=================================================");

        // 2. 검색 결과가 전혀 없다면 AI API를 호출하지 않고 즉시 리턴 (토큰 및 시간 절약)
        if (relevantDocuments.isEmpty()) {
            log.info("유사도 검색 결과 매칭되는 문서가 없습니다. AI API 호출을 생략합니다.");
            return new ChatResponse("제공된 문서에서 관련된 정보를 찾을 수 없습니다.", List.of());
        }

        // 3. 참조 문서 메타데이터 추출 (파일명 기준으로 그룹화, 최상위 유사도 기준 정렬 및 티어 부여)
        class FileGroup {
            String fileName;
            java.util.Set<String> pages = new java.util.HashSet<>();
            double bestDistance = Double.MAX_VALUE;
        }

        Map<String, FileGroup> groupMap = new java.util.HashMap<>();
        for (Document doc : relevantDocuments) {
            String file = (String) doc.getMetadata().getOrDefault("source_file", "Unknown File");
            String page = String.valueOf(doc.getMetadata().getOrDefault("page_number", "Unknown Page"));
            double distance = doc.getMetadata().containsKey("distance") ? ((Number) doc.getMetadata().get("distance")).doubleValue() : 1.0;

            FileGroup group = groupMap.computeIfAbsent(file, k -> new FileGroup());
            group.fileName = file;
            group.pages.add(page);
            if (distance < group.bestDistance) {
                group.bestDistance = distance;
            }
        }

        List<ChatResponse.SourceItem> sources = groupMap.values().stream()
                .sorted(java.util.Comparator.comparingDouble(g -> g.bestDistance)) // 유사도 높은 순(거리가 짧은 순) 정렬
                .map(group -> {
                    String pages = group.pages.stream()
                            .sorted((p1, p2) -> {
                                try {
                                    return Integer.compare(Integer.parseInt(p1), Integer.parseInt(p2));
                                } catch (NumberFormatException e) {
                                    return p1.compareTo(p2);
                                }
                            })
                            .collect(Collectors.joining(", "));
                            
                    String text = String.format("%s (Page %s)", group.fileName, pages);
                    
                    // 티어(Tier) 구분 로직
                    int tier;
                    if (group.bestDistance <= 0.38) {
                        tier = 1; // 초록/에메랄드 (유사도 매우 높음)
                    } else if (group.bestDistance <= 0.45) {
                        tier = 2; // 탁한 초록 (유사도 높음)
                    } else {
                        tier = 3; // 회색 (유사도 보통)
                    }
                    
                    return new ChatResponse.SourceItem(text, tier);
                })
                .collect(Collectors.toList());

        // 3. 프롬프트 구성 및 답변 생성
        String context = relevantDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
                
        String systemPromptTemplate = """
                당신은 문서 기반 질의응답(RAG) 어시스턴트입니다.
                아래 제공된 [Context] 정보만을 사용하여 사용자의 [Question]에 한국어로 답변하세요.
                [Context]에 없는 내용이거나 관련 정보를 찾을 수 없는 경우, 반드시 'E999'라는 코드만을 답변으로 출력하세요. 다른 설명이나 사족은 덧붙이지 마십시오.
                
                [Context]
                %s
                """;
        
        String truncatedContext = context.length() > 20 ? context.substring(0, 20) + " ...[중략]..." : context;
        String logSystemPrompt = String.format(systemPromptTemplate, truncatedContext);
        String resolvedSystemPrompt = String.format(systemPromptTemplate, context);

        log.info("========== [AI API Request Logging] ==========");
        log.info("HTTP Method: POST");
        log.info("Endpoint: https://api.openai.com/v1/chat/completions");
        log.info("Request Type: Spring AI ChatClient Prompt");
        log.info("User Question: {}", question);
        log.info("System Prompt: \n{}", logSystemPrompt);
        log.info("==============================================");

        String answer = chatClient.prompt()
                .system(resolvedSystemPrompt)
                .user(question)
                .call()
                .content();

        boolean isNotFound = answer.contains("E999");

        if (isNotFound) {
            return new ChatResponse("제공된 문서에서 관련된 정보를 찾을 수 없습니다.", List.of());
        }

        return new ChatResponse(answer, sources);
    }
}
