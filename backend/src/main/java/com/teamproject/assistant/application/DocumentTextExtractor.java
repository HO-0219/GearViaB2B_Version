package com.teamproject.assistant.application;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/**
 * 자료 파일에서 색인할 본문을 뽑는다.
 *
 * <p>지원 범위는 txt·csv·pdf·docx·xlsx·pptx 다. pdf는 기존에 주간 리포트 PDF 생성에 쓰던
 * openhtmltopdf-pdfbox가 끌어오는 PDFBox를, 나머지는 새로 추가한 POI 를 쓴다.
 */
@Component
public class DocumentTextExtractor {
    private static final Set<String> SUPPORTED = Set.of("txt", "csv", "pdf", "docx", "xlsx", "pptx");
    private static final char BOM = '﻿';

    public boolean supports(String filename) {
        return SUPPORTED.contains(extension(filename));
    }

    public String extract(byte[] content, String filename) {
        return switch (extension(filename)) {
            case "txt" -> decode(content);
            case "csv" -> fromCsv(content);
            case "pdf" -> fromPdf(content);
            case "docx" -> fromDocx(content);
            case "xlsx" -> fromXlsx(content);
            case "pptx" -> fromPptx(content);
            default -> "";
        };
    }

    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 한글 자료는 CP949 로 저장된 것이 흔하다. UTF-8 로 깨지면 다음 후보로 넘어간다. */
    private String decode(byte[] content) {
        for (Charset charset : List.of(StandardCharsets.UTF_8, Charset.forName("x-windows-949"))) {
            try {
                return stripBom(charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(content))
                        .toString());
            } catch (CharacterCodingException ignored) {
                // 다음 인코딩 후보로 넘어간다.
            }
        }
        return stripBom(new String(content, StandardCharsets.UTF_8));
    }

    /** 엑셀이 내보낸 UTF-8 파일은 앞에 BOM(U+FEFF)을 붙이는 것이 흔하다. 첫 필드를 오염시키므로 걷어낸다. */
    private String stripBom(String value) {
        return !value.isEmpty() && value.charAt(0) == BOM ? value.substring(1) : value;
    }

    /** 행을 "열이름: 값" 으로 편다. 헤더만 반복되는 청크가 검색을 망치지 않게 한다. */
    private String fromCsv(byte[] content) {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(decode(content)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) rows.add(cells(line));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("csv 본문을 읽지 못했습니다.", exception);
        }
        return rowsToText(rows);
    }

    /** 행을 "열이름: 값" 으로 편다. 헤더보다 열이 많은 행은 열N 으로, 첫 행은 헤더로 쓴다. */
    private String rowsToText(List<List<String>> rows) {
        if (rows.isEmpty()) return "";
        List<String> header = rows.get(0);
        if (rows.size() == 1) return String.join(", ", header);
        List<String> lines = new ArrayList<>();
        for (List<String> row : rows.subList(1, rows.size())) {
            List<String> pairs = new ArrayList<>();
            for (int index = 0; index < row.size(); index++) {
                if (row.get(index).isEmpty()) continue;
                String key = index < header.size() && !header.get(index).isEmpty()
                        ? header.get(index) : "열" + (index + 1);
                pairs.add(key + ": " + row.get(index));
            }
            if (!pairs.isEmpty()) lines.add(String.join(", ", pairs));
        }
        return String.join("\n", lines);
    }

    private String fromPdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document).strip();
        } catch (IOException exception) {
            throw new IllegalStateException("pdf 본문을 읽지 못했습니다.", exception);
        }
    }

    private String fromDocx(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText().strip();
        } catch (IOException exception) {
            throw new IllegalStateException("docx 본문을 읽지 못했습니다.", exception);
        }
    }

    /** 시트마다 첫 행을 헤더로 삼아 "열이름: 값" 으로 편다. 시트 이름을 앞에 붙여 구분한다. */
    private String fromXlsx(byte[] content) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            DataFormatter formatter = new DataFormatter();
            List<String> sheetsText = new ArrayList<>();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                List<List<String>> rows = new ArrayList<>();
                for (Row row : sheet) {
                    rows.add(rowValues(row, formatter));
                }
                String text = rowsToText(rows);
                if (!text.isBlank()) sheetsText.add(sheet.getSheetName() + "\n" + text);
            }
            return String.join("\n\n", sheetsText);
        } catch (IOException exception) {
            throw new IllegalStateException("xlsx 본문을 읽지 못했습니다.", exception);
        }
    }

    private List<String> rowValues(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < row.getLastCellNum(); index++) {
            var cell = row.getCell(index);
            values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return values;
    }

    /** 슬라이드마다 도형의 본문 텍스트를 모은다. 슬라이드 번호를 앞에 붙여 구분한다. */
    private String fromPptx(byte[] content) {
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(content))) {
            List<String> slidesText = new ArrayList<>();
            int slideNumber = 0;
            for (XSLFSlide slide : slideShow.getSlides()) {
                slideNumber++;
                List<String> lines = new ArrayList<>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) lines.add(text.strip());
                    }
                }
                if (!lines.isEmpty()) slidesText.add("슬라이드 " + slideNumber + "\n" + String.join("\n", lines));
            }
            return String.join("\n\n", slidesText);
        } catch (IOException exception) {
            throw new IllegalStateException("pptx 본문을 읽지 못했습니다.", exception);
        }
    }

    /** 큰따옴표로 감싼 값과 그 안의 쉼표만 다루는 최소 CSV 분해다. */
    private List<String> cells(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        values.add(current.toString().trim());
        return values;
    }
}
