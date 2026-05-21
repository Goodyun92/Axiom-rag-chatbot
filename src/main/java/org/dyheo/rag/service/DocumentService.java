package org.dyheo.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final VectorStore vectorStore;

    public void processAndSavePdf(MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            log.info("Processing PDF file: {}", filename);

            Resource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource,
                    PdfDocumentReaderConfig.builder()
                            .withPageTopMargin(0)
                            .withPageBottomMargin(0)
                            .withPagesPerDocument(1)
                            .build());

            List<Document> documents = pdfReader.get();
            log.info("Extracted {} pages from PDF", documents.size());

            // Add filename metadata to each page
            documents.forEach(doc -> {
                doc.getMetadata().put("source_file", filename);
            });

            TokenTextSplitter textSplitter = new TokenTextSplitter();
            List<Document> splitDocuments = textSplitter.apply(documents);
            log.info("Split into {} document chunks", splitDocuments.size());

            vectorStore.add(splitDocuments);
            log.info("Successfully saved chunks to VectorStore");

        } catch (IOException e) {
            log.error("Failed to process PDF file", e);
            throw new RuntimeException("PDF 파일 처리 중 오류가 발생했습니다.", e);
        }
    }
}
