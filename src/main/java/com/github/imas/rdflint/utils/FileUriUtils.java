package com.github.imas.rdflint.utils;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.stream.Collectors;

public class FileUriUtils {

  /**
   * convert uri to filepath.
   */
  public static String convertUri2FilePath(String uri) {
    String fileSeparator = File.separatorChar == '\\' ? "\\\\" : File.separator;
    String prefix = File.separatorChar == '\\' ? "file:///" : "file://";
    String fullPathEncoded = uri.substring(prefix.length());
    String fullPath = Arrays.stream(fullPathEncoded.split("/")).map(raw -> {
      String decoded = raw;
      try {
        decoded = URLDecoder.decode(raw, "UTF-8");
      } catch (UnsupportedEncodingException ex) {
        // pass
      }
      return decoded;
    }).collect(Collectors.joining(fileSeparator));
    if (fullPath.split(fileSeparator)[1].endsWith(":")) {
      fullPath = fullPath.substring(1);
    }
    return fullPath;
  }

  /**
   * convert filepath to uri.
   */
  public static String convertFilePath2Uri(String filePath) {
    String fileSeparator = File.separatorChar == '\\' ? "\\\\" : File.separator;
    String prefix = filePath.charAt(0) == '/' ? "file://" : "file:///";
    return prefix + Arrays.stream(filePath.split(fileSeparator))
        .map(raw -> {
          String decoded = raw;
          try {
            decoded = URLEncoder.encode(raw, "UTF-8");
          } catch (UnsupportedEncodingException ex) {
            // pass
          }
          return decoded;
        }).collect(Collectors.joining("/"));
  }

}
