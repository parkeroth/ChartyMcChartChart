package com.roamingroths.cmcc.utils;

import com.google.common.base.Strings;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Created by parkeroth on 5/14/17.
 */

public class DateUtil {

  private static final String PATTERN = "yyyy-MM-dd";
  private static final DateTimeFormatter FORMAT = DateTimeFormat.forPattern(PATTERN);

  public static String toWireStr(LocalDate date) {
    if (date == null) {
      return null;
    }
    return FORMAT.print(date);
  }

  public static LocalDate fromWireStr(String dateStr) {
    if (Strings.isNullOrEmpty(dateStr)) {
      return null;
    }
    return FORMAT.parseLocalDate(dateStr);
  }

  public static LocalDate now() {
    return LocalDate.now();
  }
}
