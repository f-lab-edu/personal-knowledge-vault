package com.pkv.worker.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.source.service.S3FileStorage;
import com.pkv.worker.dto.ParsedDocument;
import com.pkv.worker.dto.ParsedDocument.PageOffset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("worker")
@RequiredArgsConstructor
public class DocumentParser {

    private final S3FileStorage s3FileStorage;

    public ParsedDocument parse(String storagePath, String fileExtension) {
        byte[] bytes = s3FileStorage.downloadObject(storagePath);

        return switch (fileExtension.toLowerCase()) {
            case "pdf" -> parsePdf(bytes);
            case "txt", "md" -> parsePlainText(bytes);
            default -> throw new PkvException(ErrorCode.DOCUMENT_PARSE_FAILED,
                    "지원하지 않는 파일 형식: " + fileExtension);
        };
    }

    private ParsedDocument parsePdf(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new PkvException(ErrorCode.DOCUMENT_PARSE_FAILED, "PDF에 페이지가 없습니다.");
            }

            StringBuilder fullText = new StringBuilder();
            List<PageOffset> pageOffsets = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();

            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);

                int startOffset = fullText.length();
                pageOffsets.add(new PageOffset(i, startOffset));

                String pageText = stripper.getText(document);
                fullText.append(pageText);
            }

            // 스캔 이미지만 있는 PDF 등 텍스트가 없는 경우
            // TODO: OCR 기능 추가
            if (fullText.toString().isBlank()) {
                throw new PkvException(ErrorCode.DOCUMENT_PARSE_FAILED, "PDF에서 텍스트를 추출할 수 없습니다.");
            }

            return new ParsedDocument(fullText.toString(), pageOffsets);
        } catch (PkvException e) {
            throw e;
        } catch (IOException e) {
            throw new PkvException(ErrorCode.DOCUMENT_PARSE_FAILED, e);
        }
    }

    private ParsedDocument parsePlainText(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.trim().isEmpty()) {
            throw new PkvException(ErrorCode.DOCUMENT_PARSE_FAILED, "파일이 비어있습니다.");
        }
        return new ParsedDocument(text, List.of(new PageOffset(1, 0)));
    }
}
