package com.adsdashboard.cac;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CAC 시트(주간보호) 두 탭을 파싱해 월별 본부/센터 단위 스냅샷 생성.
 *
 * 탭1 ("본부 신규/비용/CAC_26년") 컬럼 구조 (모두 주간보호):
 *   A   본부 | B 서비스 | C 센터 | D~K  월1~8 신규수급자 (센터)
 *   M   본부 | N~U  월1~8 신규수급자 (본부)
 *   W   본부 | X 서비스 | Y~AF 월1~8 비용 (본부, ₩)
 *   AH  본부 | AI~AP 월1~8 CAC (본부, ₩)
 *
 * 탭2 ("센터별 비용/CAC_서비스 분류") — row 2 헤더, row 3+ 데이터:
 *   A   본부 | B 서비스 | C 센터 | D~K  월1~8 CAC (센터, ₩)
 *   N   본부 | O 서비스 | P 센터 | Q~X  월1~8 비용 (센터, ₩)
 */
@Component
public class CacSheetFetcher {

  private static final Logger log = LoggerFactory.getLogger(CacSheetFetcher.class);
  private static final String APP_NAME = "ads-dashboard";
  private static final int MONTHS = 12; // 1~12, 시트엔 보통 1~8만 채워짐

  private final CacProperties props;

  public CacSheetFetcher(CacProperties props) { this.props = props; }

  public CacSnapshot fetch() {
    if (!props.isConfigured()) return CacSnapshot.empty();
    try {
      Sheets sheets = buildClient();
      String id = parseId(props.sheetId());
      List<List<Object>> hq = readTab(sheets, id, props.hqTab());
      List<List<Object>> cn = readTab(sheets, id, props.centerTab());
      return parse(hq, cn);
    } catch (Exception e) {
      log.error("Failed to fetch CAC sheet", e);
      return CacSnapshot.empty();
    }
  }

  private CacSnapshot parse(List<List<Object>> hqRows, List<List<Object>> cnRows) {
    // Build month labels — 2026-01 .. 2026-12 (주간보호 26년 시트 기준)
    List<String> months = new java.util.ArrayList<>();
    for (int m = 1; m <= MONTHS; m++) months.add(String.format("2026-%02d", m));

    Map<String, MonthlyAcc> byHq = new LinkedHashMap<>();
    Map<String, CenterAcc> byCenter = new LinkedHashMap<>();

    // ---- 탭1 파싱: row 4(idx 4)부터 데이터 ----
    for (int r = 4; r < hqRows.size(); r++) {
      List<Object> row = hqRows.get(r);
      if (row == null || row.isEmpty()) continue;

      // Section 1 (A~K): 센터별 신규수급자
      String hq1 = trim(cell(row, 0));
      String svc1 = trim(cell(row, 1));
      String center1 = trim(cell(row, 2));
      if (notBlank(center1)) {
        CenterAcc a = byCenter.computeIfAbsent(center1, k -> new CenterAcc(hq1, svc1));
        for (int m = 0; m < 8; m++) {
          Long u = parseInt(cell(row, 3 + m));
          if (u != null) a.users[m] = u;
        }
      }

      // Section 2 (M~U): 본부 신규수급자
      String hq2 = trim(cell(row, 12));
      if (notBlank(hq2)) {
        MonthlyAcc a = byHq.computeIfAbsent(hq2, k -> new MonthlyAcc());
        for (int m = 0; m < 8; m++) {
          Long u = parseInt(cell(row, 13 + m));
          if (u != null) a.users[m] = u;
        }
      }

      // Section 3 (W~AF): 본부 비용
      String hq3 = trim(cell(row, 22));
      if (notBlank(hq3)) {
        MonthlyAcc a = byHq.computeIfAbsent(hq3, k -> new MonthlyAcc());
        for (int m = 0; m < 8; m++) {
          Double c = parseWon(cell(row, 24 + m));
          if (c != null) a.cost[m] = c;
        }
      }

      // Section 4 (AH~AP): 본부 CAC (직접값)
      String hq4 = trim(cell(row, 33));
      if (notBlank(hq4)) {
        MonthlyAcc a = byHq.computeIfAbsent(hq4, k -> new MonthlyAcc());
        for (int m = 0; m < 8; m++) {
          Double c = parseWon(cell(row, 34 + m));
          if (c != null) a.cac[m] = c;
        }
      }
    }

    // ---- 탭2 파싱: row 3(idx 3)부터 데이터 ----
    for (int r = 3; r < cnRows.size(); r++) {
      List<Object> row = cnRows.get(r);
      if (row == null || row.isEmpty()) continue;

      // Section 1 (A~K): 센터 CAC
      String hq1 = trim(cell(row, 0));
      String svc1 = trim(cell(row, 1));
      String center1 = trim(cell(row, 2));
      if (notBlank(center1)) {
        CenterAcc a = byCenter.computeIfAbsent(center1, k -> new CenterAcc(hq1, svc1));
        if (a.hq == null) a.hq = hq1;
        if (a.service == null) a.service = svc1;
        for (int m = 0; m < 8; m++) {
          Double c = parseWon(cell(row, 3 + m));
          if (c != null) a.cac[m] = c;
        }
      }

      // Section 2 (N~X): 센터 비용
      String hq2 = trim(cell(row, 13));
      String svc2 = trim(cell(row, 14));
      String center2 = trim(cell(row, 15));
      if (notBlank(center2)) {
        CenterAcc a = byCenter.computeIfAbsent(center2, k -> new CenterAcc(hq2, svc2));
        if (a.hq == null) a.hq = hq2;
        for (int m = 0; m < 8; m++) {
          Double c = parseWon(cell(row, 16 + m));
          if (c != null) a.cost[m] = c;
        }
      }
    }

    // ---- 전체 합계: 본부 합산 ----
    MonthlyAcc totals = new MonthlyAcc();
    for (MonthlyAcc a : byHq.values()) {
      for (int m = 0; m < MONTHS; m++) {
        if (a.cost[m] > 0) totals.cost[m] += a.cost[m];
        if (a.users[m] > 0) totals.users[m] += a.users[m];
      }
    }
    // total CAC = total cost / total users
    for (int m = 0; m < MONTHS; m++) {
      if (totals.users[m] > 0 && totals.cost[m] > 0) {
        totals.cac[m] = totals.cost[m] / totals.users[m];
      }
    }

    return new CacSnapshot(months, totals, byHq, byCenter);
  }

  // ---- helpers ----

  private static String cell(List<Object> row, int i) {
    if (i >= row.size()) return null;
    Object v = row.get(i);
    return v == null ? null : v.toString();
  }
  private static String trim(String s) { return s == null ? null : s.trim(); }
  private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

  private static Double parseWon(String s) {
    if (s == null) return null;
    String t = s.replaceAll("[₩,\\s]", "").trim();
    if (t.isEmpty() || "-".equals(t)) return null;
    try { return Double.parseDouble(t); } catch (NumberFormatException e) { return null; }
  }

  private static Long parseInt(String s) {
    if (s == null) return null;
    String t = s.replaceAll("[,\\s]", "").trim();
    if (t.isEmpty() || "-".equals(t)) return null;
    try { return (long) Double.parseDouble(t); } catch (NumberFormatException e) { return null; }
  }

  private List<List<Object>> readTab(Sheets sheets, String sheetId, String tab) throws IOException {
    String range = "'" + tab.replace("'", "''") + "'!A1:AZ200";
    ValueRange resp = sheets.spreadsheets().values().get(sheetId, range).execute();
    List<List<Object>> v = resp.getValues();
    return v == null ? List.of() : v;
  }

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

  private static String parseId(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    var m = java.util.regex.Pattern.compile("/spreadsheets/d/([a-zA-Z0-9_-]+)").matcher(s);
    return m.find() ? m.group(1) : s;
  }

  // ---- accumulator classes ----

  static final class MonthlyAcc {
    final double[] cost = new double[MONTHS];
    final long[] users = new long[MONTHS];
    final double[] cac = new double[MONTHS];
  }

  static final class CenterAcc {
    String hq;
    String service;
    final double[] cost = new double[MONTHS];
    final long[] users = new long[MONTHS];
    final double[] cac = new double[MONTHS];
    CenterAcc(String hq, String service) { this.hq = hq; this.service = service; }
  }
}
