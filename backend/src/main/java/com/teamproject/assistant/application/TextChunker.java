package com.teamproject.assistant.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 본문을 검색 단위로 자른다. 900자 / 겹침 150자는 파이썬 시제품에서 12문항 평가로 재본 값이다.
 *
 * <p>문단 → 줄 → 문장 → 공백 순으로 경계를 찾고, 그래도 안 되면 글자 수로 끊는다.
 * 겹침을 두는 이유는 규정 한 문장이 청크 경계에서 반으로 갈리면 그 문장으로는 검색이 안 되기 때문이다.
 */
@Component
public class TextChunker {
    private static final int SIZE = 900;
    private static final int OVERLAP = 150;
    private static final List<String> SEPARATORS = List.of("\n\n", "\n", ". ", " ");

    public List<String> split(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").strip();
        if (normalized.isEmpty()) return List.of();
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String segment : segments(normalized)) {
            if (current.length() + segment.length() > SIZE && !current.isEmpty()) {
                chunks.add(current.toString().strip());
                String tail = overlapOf(current.toString());
                current.setLength(0);
                // 겹침까지 얹으면 상한을 넘는 조각이면 겹침을 포기한다. 상한이 우선이다.
                if (tail.length() + segment.length() <= SIZE) current.append(tail);
            }
            current.append(segment);
        }
        if (!current.toString().isBlank()) chunks.add(current.toString().strip());
        return chunks;
    }

    /** 자를 수 있는 가장 큰 단위부터 시도해, 모든 조각이 SIZE 이하가 되게 만든다. */
    private List<String> segments(String text) {
        List<String> pending = new ArrayList<>(List.of(text));
        for (String separator : SEPARATORS) {
            List<String> next = new ArrayList<>();
            for (String piece : pending) {
                if (piece.length() <= SIZE) {
                    next.add(piece);
                    continue;
                }
                String[] parts = piece.split(java.util.regex.Pattern.quote(separator), -1);
                for (int index = 0; index < parts.length; index++) {
                    // 구분자를 붙여 두어야 이어 붙였을 때 원문이 복원된다.
                    next.add(index == parts.length - 1 ? parts[index] : parts[index] + separator);
                }
            }
            pending = next;
        }
        List<String> result = new ArrayList<>();
        for (String piece : pending) {
            for (int start = 0; start < piece.length(); start += SIZE) {
                result.add(piece.substring(start, Math.min(piece.length(), start + SIZE)));
            }
        }
        return result;
    }

    private String overlapOf(String chunk) {
        return chunk.length() <= OVERLAP ? chunk : chunk.substring(chunk.length() - OVERLAP);
    }
}
