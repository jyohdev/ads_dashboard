package com.adsdashboard.cac;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CacSnapshot(
    List<String> months,
    CacSheetFetcher.MonthlyAcc totals,
    Map<String, CacSheetFetcher.MonthlyAcc> byHq,
    Map<String, CacSheetFetcher.CenterAcc> byCenter) {

  public static CacSnapshot empty() {
    return new CacSnapshot(List.of(), new CacSheetFetcher.MonthlyAcc(), new LinkedHashMap<>(), new LinkedHashMap<>());
  }

  public Map<String, Object> toApiPayload() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("months", months);
    out.put("totals", monthlyToMap(totals));
    Map<String, Object> hqMap = new LinkedHashMap<>();
    byHq.forEach((k, v) -> hqMap.put(k, monthlyToMap(v)));
    out.put("byHq", hqMap);
    Map<String, Object> cMap = new LinkedHashMap<>();
    byCenter.forEach((k, v) -> {
      Map<String, Object> m = monthlyToMap(v);
      m.put("hq", v.hq);
      m.put("service", v.service);
      cMap.put(k, m);
    });
    out.put("byCenter", cMap);
    return out;
  }

  private static Map<String, Object> monthlyToMap(CacSheetFetcher.MonthlyAcc a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("cost", toBoxed(a.cost));
    m.put("users", toBoxedLong(a.users));
    m.put("cac", toBoxed(a.cac));
    return m;
  }

  private static Map<String, Object> monthlyToMap(CacSheetFetcher.CenterAcc a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("cost", toBoxed(a.cost));
    m.put("users", toBoxedLong(a.users));
    m.put("cac", toBoxed(a.cac));
    return m;
  }

  private static Double[] toBoxed(double[] a) {
    Double[] b = new Double[a.length];
    for (int i = 0; i < a.length; i++) b[i] = a[i] == 0 ? null : a[i];
    return b;
  }

  private static Long[] toBoxedLong(long[] a) {
    Long[] b = new Long[a.length];
    for (int i = 0; i < a.length; i++) b[i] = a[i] == 0 ? null : a[i];
    return b;
  }
}
