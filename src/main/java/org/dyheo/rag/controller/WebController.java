package org.dyheo.rag.controller;

import lombok.RequiredArgsConstructor;
import org.dyheo.rag.dto.ChatResponse;
import org.dyheo.rag.service.DocumentService;
import org.dyheo.rag.service.RagChatService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebController {

    private final DocumentService documentService;
    private final RagChatService ragChatService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/")
    public String index() {
        return "redirect:/documents";
    }

    @GetMapping("/documents")
    public String documents(Model model) {
        try {
            // PostgreSQL JSONB를 쿼리하여 중복 없는 파일명 목록 조회
            List<Map<String, Object>> files = jdbcTemplate.queryForList(
                    "SELECT DISTINCT metadata->>'source_file' as file_name FROM vector_store WHERE metadata->>'source_file' IS NOT NULL"
            );
            model.addAttribute("files", files);
        } catch (Exception e) {
            // 테이블이 아직 생성되지 않았을 경우 무시
            model.addAttribute("files", List.of());
        }
        return "documents";
    }

    @PostMapping("/documents/upload")
    public String uploadDocument(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "파일을 선택해주세요.");
            return "redirect:/documents";
        }
        try {
            log.info("문서 추가 요청됨: 파일명={}", file.getOriginalFilename());
            documentService.processAndSavePdf(file);
            log.info("문서 추가 성공: 파일명={}", file.getOriginalFilename());
            redirectAttributes.addFlashAttribute("message", "파일 업로드 및 벡터화가 성공적으로 완료되었습니다.");
        } catch (Exception e) {
            log.error("문서 추가 중 오류 발생: 파일명={}", file.getOriginalFilename(), e);
            redirectAttributes.addFlashAttribute("error", "파일 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/documents";
    }

    @PostMapping("/documents/delete")
    public String deleteDocument(@RequestParam("fileName") String fileName, RedirectAttributes redirectAttributes) {
        try {
            log.info("문서 삭제 요청됨: 파일명={}", fileName);
            int deletedRows = jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'source_file' = ?", fileName);
            if (deletedRows > 0) {
                log.info("문서 삭제 성공: 파일명={}, 삭제된 청크 수={}", fileName, deletedRows);
                redirectAttributes.addFlashAttribute("message", fileName + " 문서가 삭제되었습니다.");
            } else {
                log.warn("문서 삭제 실패(찾을 수 없음): 파일명={}", fileName);
                redirectAttributes.addFlashAttribute("error", "삭제할 문서를 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            log.error("문서 삭제 중 오류 발생: 파일명={}", fileName, e);
            redirectAttributes.addFlashAttribute("error", "삭제 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/documents";
    }

    @GetMapping("/chat")
    public String chat() {
        return "chat";
    }

    @PostMapping("/chat/ask")
    @ResponseBody
    public ChatResponse ask(@RequestParam("question") String question) {
        return ragChatService.generateAnswer(question);
    }
}
