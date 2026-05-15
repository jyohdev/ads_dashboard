package com.adsdashboard.lead;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Google Sheets API + 서비스 계정으로 신규콜 데이터 조회.
 *
 * 시트 헤더는 LeadCsvFetcher와 동일 규칙(한국어/영어 alias)으로 매핑됨.
 *  - 상담일자 / date
 *  - 본부명 / hq
 *  - 주간보호 센터명 / daycare
 *  - 방문요양 센터명 / homecare
 *
 * 시트 미설정 시 빈 리스트 반환 (LeadService에서 CSV fallback으로 넘어감).
 */
@Component
public class LeadSheetFetcher implements LeadFetcher {

  private static final Logger log = LoggerFactory.getLogger(LeadSheetFetcher.class);
  private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
      DateTimeFormatter.ofPattern("yyyy-MM-dd"),
      DateTimeFormatter.ofPattern("yyyy/MM/dd"),
      DateTimeFormatter.ofPattern("yyyy.MM.dd"),
      DateTimeFormatter.ofPattern("yyyy. MM. dd"),
      DateTimeFormatter.ofPattern("yyyy. M. d"));

  private static final String APP_NAME = "ads-dashboard";

  private final LeadProperties props;

  public LeadSheetFetcher(LeadProperties props) {
    this.props = props;
  }

  @Override
  public List<LeadEntry> fetchAll() {
    if (!props.isSheetConfigured()) return List.of();
    List<LeadEntry> merged = new ArrayList<>();
    try {
      Sheets sheets = buildClient();
      for (String raw : props.sheetIds()) {
        SheetRef ref = parseRef(raw);
        try {
          String range = resolveRange(sheets, ref);
          ValueRange resp = sheets.spreadsheets().values()
              .get(ref.id, range)
              .execute();
          List<List<Object>> values = resp.getValues();
          if (values == null || values.isEmpty()) continue;
          log.info("Sheet {} (range={}) → {} rows", ref.id, range, values.size() - 1);
          merged.addAll(parse(values));
        } catch (Exception e) {
          log.error("Failed to read sheet {} (gid={})", ref.id, ref.gid, e);
        }
      }
    } catch (Exception e) {
      log.error("Failed to build Sheets client", e);
    }
    return merged;
  }

  /** URL 또는 plain ID에서 sheetId + (옵션) gid 분리. */
  private static SheetRef parseRef(String raw) {
    String s = raw.trim();
    String id = s;
    Long gid = null;
    // URL이면 ID 추출
    java.util.regex.Matcher mId = java.util.regex.Pattern
        .compile("/spreadsheets/d/([a-zA-Z0-9_-]+)").matcher(s);
    if (mId.find()) id = mId.group(1);
    // gid 추출 (URL hash, query, "#gid=" 등 어디든)
    java.util.regex.Matcher mGid = java.util.regex.Pattern
        .compile("gid=([0-9]+)").matcher(s);
    if (mGid.find()) {
      try { gid = Long.parseLong(mGid.group(1)); } catch (NumberFormatException ignore) {}
    }
    return new SheetRef(id, gid);
  }

  /** gid가 있으면 metadata로 탭 이름 찾아서 "<탭>!A:Z" 반환. 없으면 props.sheetRange()를 그대로. */
  private String resolveRange(Sheets sheets, SheetRef ref) throws IOException {
    if (ref.gid == null) return props.sheetRange();
    Spreadsheet meta = sheets.spreadsheets().get(ref.id).execute();
    for (Sheet sh : meta.getSheets()) {
      SheetProperties p = sh.getProperties();
      if (p != null && p.getSheetId() != null && p.getSheetId().longValue() == ref.gid) {
        String title = p.getTitle();
        return "'" + title.replace("'", "''") + "'!A:Z";
      }
    }
    // gid 매칭 실패 시 fallback
    return props.sheetRange();
  }

  private record SheetRef(String id, Long gid) {}

  private List<LeadEntry> parse(List<List<Object>> values) {
    List<String> headers = new ArrayList<>();
    for (Object o : values.get(0)) headers.add(o == null ? "" : o.toString().trim());
    Map<String, Integer> idx = headerIndex(headers);
    Integer dateIdx = idx.get("date");
    Integer hqIdx = idx.get("hq");
    Integer dcIdx = idx.get("daycare");
    Integer hcIdx = idx.get("homecare");

    List<LeadEntry> rows = new ArrayList<>();
    for (int i = 1; i < values.size(); i++) {
      List<Object> row = values.get(i);
      if (row == null || row.isEmpty()) continue;
      LocalDate d = dateIdx != null ? parseDate(cell(row, dateIdx)) : null;
      if (d == null) continue;
      String hq = hqIdx != null ? blankToNull(cell(row, hqIdx)) : null;
      String dc = dcIdx != null ? blankToNull(cell(row, dcIdx)) : null;
      String hc = hcIdx != null ? blankToNull(cell(row, hcIdx)) : null;
      rows.add(new LeadEntry(d, hq, dc, hc));
    }
    return rows;
  }

  private Sheets buildClient() throws IOException, java.security.GeneralSecurityException {
    NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
    // SPREADSHEETS_READONLY: 값 + metadata(탭 이름) 둘 다 읽기 가능
    GoogleCredentials creds = loadCredentials().createScoped(List.of(SheetsScopes.SPREADSHEETS_READONLY));
    HttpRequestInitializer init = new HttpCredentialsAdapter(creds);
    return new Sheets.Builder(transport, GsonFactory.getDefaultInstance(), init)
        .setApplicationName(APP_NAME)
        .build();
  }

  private GoogleCredentials loadCredentials() throws IOException {
    String inline = props.serviceAccountKey();
    if (inline != null && !inline.isBlank()) {
      InputStream is = new ByteArrayInputStream(inline.getBytes(StandardCharsets.UTF_8));
      return GoogleCredentials.fromStream(is);
    }
    String path = props.serviceAccountKeyPath();
    try (FileInputStream fis = new FileInputStream(path)) {
      return GoogleCredentials.fromStream(fis);
    }
  }

  private static String cell(List<Object> row, int i) {
    if (i >= row.size()) return null;
    Object v = row.get(i);
    return v == null ? null : v.toString();
  }

  private static Map<String, Integer> headerIndex(List<String> headers) {
    Map<String, Integer> m = new HashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      String h = headers.get(i).toLowerCase().replace(" ", "");
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
}
