package com.adsdashboard.lead;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * CSV 파일에서 신규콜 데이터 읽기.
 *
 * 기대 헤더(한국어/영어 모두 인식, 순서 무관):
 *  - 상담일자 / date
 *  - 본부명 / hq
 *  - 주간보호 센터명 / daycareCenter
 *  - 방문요양 센터명 / homecareCenter
 *
 * 파일 없으면 빈 리스트 반환 (에러 X).
 */
@Component
public class LeadCsvFetcher {

  private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
      DateTimeFormatter.ofPattern("yyyy-MM-dd"),
      DateTimeFormatter.ofPattern("yyyy/MM/dd"),
      DateTimeFormatter.ofPattern("yyyy.MM.dd"));

  private final LeadProperties props;

  public LeadCsvFetcher(LeadProperties props) {
    this.props = props;
  }

  public List<LeadEntry> fetchAll() {
    Path path = Paths.get(props.csvPath());
    if (!Files.exists(path)) return List.of();
    try {
      List<String> lines = Files.readAllLines(path);
      if (lines.isEmpty()) return List.of();
      List<String> headers = parseRow(lines.get(0));
      Map<String, Integer> idx = headerIndex(headers);
      Integer dateIdx = idx.get("date");
      Integer hqIdx = idx.get("hq");
      Integer dcIdx = idx.get("daycare");
      Integer hcIdx = idx.get("homecare");
      List<LeadEntry> rows = new ArrayList<>();
      for (int i = 1; i < lines.size(); i++) {
        String line = lines.get(i);
        if (line == null || line.isBlank()) continue;
        List<String> cells = parseRow(line);
        LocalDate d = dateIdx != null && cells.size() > dateIdx ? parseDate(cells.get(dateIdx)) : null;
        if (d == null) continue;
        String hq = hqIdx != null && cells.size() > hqIdx ? blankToNull(cells.get(hqIdx)) : null;
        String dc = dcIdx != null && cells.size() > dcIdx ? blankToNull(cells.get(dcIdx)) : null;
        String hc = hcIdx != null && cells.size() > hcIdx ? blankToNull(cells.get(hcIdx)) : null;
        rows.add(new LeadEntry(d, hq, dc, hc));
      }
      return rows;
    } catch (IOException e) {
      throw new RuntimeException("Failed to read leads CSV at " + path, e);
    }
  }

  private static Map<String, Integer> headerIndex(List<String> headers) {
    java.util.Map<String, Integer> m = new java.util.HashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      String h = headers.get(i).trim().toLowerCase().replace(" ", "");
      switch (h) {
        case "상담일자", "일자", "date" -> m.put("date", i);
        case "본부명", "본부", "hq" -> m.put("hq", i);
        case "주간보호센터명", "주간보호센터", "주보센터", "daycarecenter", "daycare" -> m.put("daycare", i);
        case "방문요양센터명", "방문요양센터", "방요센터", "homecarecenter", "homecare" -> m.put("homecare", i);
        default -> {}
      }
    }
    return m;
  }

  private static LocalDate parseDate(String s) {
    if (s == null || s.isBlank()) return null;
    String trimmed = s.trim();
    for (DateTimeFormatter f : DATE_FORMATS) {
      try { return LocalDate.parse(trimmed, f); } catch (Exception ignore) {}
    }
    return null;
  }

  private static String blankToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  /** 단순 CSV 파서 — 따옴표로 감싼 셀 + 쉼표 이스케이프 처리. */
  private static List<String> parseRow(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuote = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          cur.append('"');
          i++;
        } else {
          inQuote = !inQuote;
        }
      } else if (c == ',' && !inQuote) {
        out.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    out.add(cur.toString());
    return out;
  }
}
