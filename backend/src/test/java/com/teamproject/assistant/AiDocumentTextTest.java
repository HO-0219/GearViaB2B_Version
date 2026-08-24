package com.teamproject.assistant;

import com.teamproject.assistant.application.DocumentTextExtractor;
import com.teamproject.assistant.application.TextChunker;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiDocumentTextTest {
    private final DocumentTextExtractor extractor = new DocumentTextExtractor();
    private final TextChunker chunker = new TextChunker();

    @Test
    void readsBothUtf8AndCp949Text() {
        String text = "금요일 배포는 금지한다.";

        assertThat(extractor.extract(text.getBytes(StandardCharsets.UTF_8), "규정.txt")).isEqualTo(text);
        assertThat(extractor.extract(text.getBytes(Charset.forName("x-windows-949")), "규정.txt"))
                .isEqualTo(text);
    }

    @Test
    void stripsUtf8BomFromTheFirstField() {
        byte[] withBom = concat("﻿".getBytes(StandardCharsets.UTF_8),
                "월,처리건수\n2026-06,120\n".getBytes(StandardCharsets.UTF_8));

        assertThat(extractor.extract(withBom, "통계.csv")).isEqualTo("월: 2026-06, 처리건수: 120");
    }

    @Test
    void keepsColumnsBeyondTheHeaderInsteadOfDroppingThem() {
        byte[] csv = "월,처리건수\n2026-06,120,14\n".getBytes(StandardCharsets.UTF_8);

        assertThat(extractor.extract(csv, "통계.csv")).isEqualTo("월: 2026-06, 처리건수: 120, 열3: 14");
    }

    @Test
    void readsPdfText() throws Exception {
        // Standard14Fonts 는 WinAnsiEncoding(라틴 문자)만 지원해 한글은 못 그린다.
        byte[] pdf = pdfBytes("Friday deployment is forbidden.");

        assertThat(extractor.extract(pdf, "규정.pdf")).contains("Friday deployment is forbidden.");
    }

    @Test
    void readsDocxText() throws Exception {
        byte[] docx = docxBytes("금요일 배포는 금지한다.");

        assertThat(extractor.extract(docx, "규정.docx")).contains("금요일 배포는 금지한다.");
    }

    @Test
    void readsXlsxRowsAsHeaderValuePairs() throws Exception {
        byte[] xlsx = xlsxBytes(List.of(
                List.of("월", "처리건수"),
                List.of("2026-06", "120")));

        assertThat(extractor.extract(xlsx, "통계.xlsx")).contains("월: 2026-06, 처리건수: 120");
    }

    @Test
    void readsPptxSlideText() throws Exception {
        byte[] pptx = pptxBytes("금요일 배포는 금지한다.");

        assertThat(extractor.extract(pptx, "규정.pptx")).contains("금요일 배포는 금지한다.");
    }

    private byte[] pdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] docxBytes(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] xlsxBytes(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("시트1");
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                List<String> cells = rows.get(rowIndex);
                for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                    row.createCell(cellIndex).setCellValue(cells.get(cellIndex));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] pptxBytes(String text) throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSLFSlide slide = slideShow.createSlide();
            XSLFTextBox textBox = slide.createTextBox();
            textBox.setText(text);
            slideShow.write(out);
            return out.toByteArray();
        }
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    @Test
    void flattensCsvRowsSoHeadersDoNotDominateAChunk() {
        byte[] csv = """
                월,처리건수,지연건수
                2026-06,120,14
                2026-07,98,5
                """.getBytes(StandardCharsets.UTF_8);

        assertThat(extractor.extract(csv, "통계.csv"))
                .isEqualTo("월: 2026-06, 처리건수: 120, 지연건수: 14\n월: 2026-07, 처리건수: 98, 지연건수: 5");
    }

    @Test
    void reportsOnlyTheFormatsItCanActuallyRead() {
        assertThat(extractor.supports("회의록.txt")).isTrue();
        assertThat(extractor.supports("통계.csv")).isTrue();
        assertThat(extractor.supports("설계.pdf")).isTrue();
        assertThat(extractor.supports("회의록.docx")).isTrue();
        assertThat(extractor.supports("통계.xlsx")).isTrue();
        assertThat(extractor.supports("발표.pptx")).isTrue();
        assertThat(extractor.supports("사진.png")).isFalse();
        assertThat(extractor.supports(null)).isFalse();
    }

    @Test
    void keepsChunksWithinTheSizeLimitAndOverlapsThem() {
        String paragraph = "가".repeat(400);
        String text = String.join("\n\n", paragraph, paragraph, paragraph, paragraph);

        List<String> chunks = chunker.split(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(900));
        // 겹침이 있으므로 조각 길이의 합은 원문보다 길다.
        assertThat(chunks.stream().mapToInt(String::length).sum()).isGreaterThan(text.length());
    }

    @Test
    void splitsASingleOversizedParagraphInsteadOfDroppingIt() {
        List<String> chunks = chunker.split("나".repeat(2500));

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(900));
    }

    @Test
    void returnsNothingForEmptyContent() {
        assertThat(chunker.split("   \n\n ")).isEmpty();
        assertThat(chunker.split(null)).isEmpty();
    }
}
