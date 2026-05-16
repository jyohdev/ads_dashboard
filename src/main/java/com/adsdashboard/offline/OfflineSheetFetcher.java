package com.adsdashboard.offline;

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

@Component
public class OfflineSheetFetcher {

  private static final Logger log = LoggerFactory.getLogger(OfflineSheetFetcher.class);
  private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
      DateTimeFormatter.ofPattern("yyyy-MM-dd"),
      DateTimeFormatter.ofPattern("yyyy/MM/dd"),
      DateTimeFormatter.ofPattern("yyyy.MM.dd"),
      DateTimeFormatter.ofPattern("yyyy. MM. dd"),
      DateTimeFormatter.ofPattern("yyyy. M. d"));
  private static final String APP_NAME = "ads-dashboard";

  private final OfflineProperties props;

  public OfflineSheetFetcher(OfflineProperties props) {
    this.props = props;
  }

  public List<OfflineEntry> fetchAll() {
    if (!props.isConfigured()) return List.of();
    try {
      Sheets sheets = buildClient();
      SheetRef ref = parseRef(props.sheetId());
      String range = resolveRange(sheets, ref);
      ValueRange resp = sheets.spreadsheets().values().get(ref.id, range).execute();
      List<List<Object>> values = resp.getValues();
      if (values == null || values.isEmpty()) return List.of();
      log.info("Offline sheet {} (range={}) → {} rows", ref.id, range, values.size() - 1);
      return parse(values);
    } catch (Exception e) {
      log.error("Failed to read offline sheet", e);
      return List.of();
    }
  }

  private List<OfflineEntry> parse(List<List<Object>> values) {
    int hdrIdx = Math.min(props.headerRowOffset(), values.size() - 1);
    List<Object> headerRow = values.get(hdrIdx);
    List<String> headers = new ArrayList<>();
    for (Object o : headerRow) headers.add(o == null ? "" : o.toString().trim());
    Map<String, Integer> idx = headerIndex(headers);
    Integer dateI = idx.get("date");
    Integer hqI = idx.get("hq");
    Integer centerI = idx.get("center");
    Integer serviceI = idx.get("service");
    Integer typeI = idx.get("type");           // "종류" — 온라인/오프라인/센터지원/판촉물
    Integer mediaI = idx.get("offlineMedia");  // "오프라인 유입" — 신문광고, 현수막 등
    Integer noteI = idx.get("note");           // "비고" — 자유 텍스트
    Integer amountI = idx.get("amount");

    List<OfflineEntry> rows = new ArrayList<>();
    for (int i = hdrIdx + 1; i < values.size(); i++) {
      List<Object> row = values.get(i);
      if (row == null || row.isEmpty()) continue;
      // 종류 = 오프라인 / 센터지원 / 판촉물 만 포함 (온라인 제외)
      String type = typeI != null ? blankToNull(cell(row, typeI)) : null;
      if (type == null) continue;
      if (type.contains("온라인") && !type.contains("오프라인")) continue;
      LocalDate d = dateI != null ? parseDate(cell(row, dateI)) : null;
      if (d == null) continue;
      Long amount = amountI != null ? parseAmount(cell(row, amountI)) : null;
      if (amount == null || amount <= 0) continue;
      String hq = hqI != null ? blankToNull(cell(row, hqI)) : null;
      String center = centerI != null ? blankToNull(cell(row, centerI)) : null;
      String svc = serviceI != null ? blankToNull(cell(row, serviceI)) : null;
      String mediaRaw = mediaI != null ? blankToNull(cell(row, mediaI)) : null;
      String note = noteI != null ? blankToNull(cell(row, noteI)) : null;
      String mediaResolved = resolveMedia(mediaRaw, note, type);
      rows.add(new OfflineEntry(d, hq, center, svc, mediaResolved, type, note, amount));
    }
    return rows;
  }

  /** 표시용 매체 분류 결정.
   *  1) 오프라인유입 컬럼이 명시돼 있고 "기타" 가 아니면 그대로 사용
   *  2) "기타" 이거나 비어있으면 비고에서 핵심 키워드 추출 (부채/파스/물티슈/배너/현수막/리플릿/포스터/안내문/스티커/약국봉투/마트영상/포스터/가림막/판촉물스티커)
   *  3) 키워드 매칭 실패하면 종류(센터지원/판촉물/오프라인) 그대로 라벨로 사용
   */
  private static final String[] MEDIA_KEYWORDS = {
      "파스", "부채", "물티슈", "자석스티커", "판촉물스티커", "스티커",
      "약국봉투", "마트 영상", "마트영상", "차량 부착", "차량부착",
      "배너", "현수막", "리플릿", "포스터", "안내문", "안내판",
      "전단지", "깔판", "가림막", "선반", "간판"
  };

  // package-private — OfflineMediaResolveTest 에서 단위 테스트한다.
  static String resolveMedia(String mediaRaw, String note, String kind) {
    if (mediaRaw != null && !mediaRaw.isBlank() && !"기타".equals(mediaRaw)) {
      return mediaRaw;
    }
    if (note != null && !note.isBlank()) {
      for (String kw : MEDIA_KEYWORDS) {
        if (note.contains(kw)) return kw;
      }
    }
    if (kind != null && !kind.isBlank() && !"오프라인".equals(kind)) {
      return kind; // 센터지원 / 판촉물 — 더 자세한 매체 못 찾으면 종류 자체를 라벨로
    }
    return "기타";
  }

  private static Map<String, Integer> headerIndex(List<String> headers) {
    Map<String, Integer> m = new HashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      String h = headers.get(i).toLowerCase().replace(" ", "");
      switch (h) {
        case "실제결제일자", "결제일자", "결제일", "일자", "날짜", "date" -> m.put("date", i);
        case "본부", "본부명", "hq" -> m.put("hq", i);
        case "센터", "센터명", "center" -> m.put("center", i);
        case "서비스", "service" -> m.put("service", i);
        case "종류", "구분", "type" -> m.put("type", i);
        case "오프라인유입", "오프라인", "오프라인매체", "offline" -> m.put("offlineMedia", i);
        case "비고", "메모", "note", "remark" -> m.put("note", i);
        case "금액(원)", "금액", "amount", "비용" -> m.put("amount", i);
        default -> {}
      }
    }
    return m;
  }

  private static Long parseAmount(String s) {
    if (s == null) return null;
    String cleaned = s.replaceAll("[₩,\\s]", "").trim();
    if (cleaned.isEmpty()) return null;
    try { return (long) Double.parseDouble(cleaned); } catch (NumberFormatException e) { return null; }
  }

  private static LocalDate parseDate(String s) {
    if (s == null || s.isBlank()) return null;
    String t = s.trim();
    for (DateTimeFormatter f : DATE_FORMATS) {
      try { return LocalDate.parse(t, f); } catch (Exception ignore) {}
    }
    return null;
  }

  private static String cell(List<Object> row, int i) {
    if (i >= row.size()) return null;
    Object v = row.get(i);
    return v == null ? null : v.toString();
  }

  private static String blankToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  // ---- Sheets client + URL parsing (LeadSheetFetcher 와 동일 패턴) ----

  private Sheets buildClient() throws IOException, java.security.GeneralSecurityException {
    NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
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
    try (FileInputStream fis = new FileInputStream(props.serviceAccountKeyPath())) {
      return GoogleCredentials.fromStream(fis);
    }
  }

  private record SheetRef(String id, Long gid) {}

  private static SheetRef parseRef(String raw) {
    String s = raw.trim();
    String id = s;
    Long gid = null;
    var mId = java.util.regex.Pattern.compile("/spreadsheets/d/([a-zA-Z0-9_-]+)").matcher(s);
    if (mId.find()) id = mId.group(1);
    var mGid = java.util.regex.Pattern.compile("gid=([0-9]+)").matcher(s);
    if (mGid.find()) {
      try { gid = Long.parseLong(mGid.group(1)); } catch (NumberFormatException ignore) {}
    }
    return new SheetRef(id, gid);
  }

  private String resolveRange(Sheets sheets, SheetRef ref) throws IOException {
    if (ref.gid == null) return props.sheetRange();
    Spreadsheet meta = sheets.spreadsheets().get(ref.id).execute();
    for (Sheet sh : meta.getSheets()) {
      SheetProperties p = sh.getProperties();
      if (p != null && p.getSheetId() != null && p.getSheetId().longValue() == ref.gid) {
        return "'" + p.getTitle().replace("'", "''") + "'!A:Z";
      }
    }
    return props.sheetRange();
  }
}
