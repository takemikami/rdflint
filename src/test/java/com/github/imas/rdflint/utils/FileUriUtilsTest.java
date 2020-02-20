package com.github.imas.rdflint.utils;

import static junit.framework.TestCase.assertEquals;

import com.github.imas.rdflint.RdfLintLanguageServer;
import java.io.File;
import org.junit.Test;

public class FileUriUtilsTest {

  @Test
  public void convertUri2FilePath() throws Exception {
    String path1 = String.join(File.separator, new String[]{"home", "user", "rdflint"});
    assertEquals("/" + path1,
        RdfLintLanguageServer.convertUri2FilePath("file:///home/user/rdflint"));

    String path2 = String.join(File.separator, new String[]{"C:", "rdflint"});
    assertEquals(path2,
        RdfLintLanguageServer.convertUri2FilePath("file:///C%3A/rdflint"));
  }

  @Test
  public void convertFilePath2Uri() throws Exception {
    String path1 = String.join(File.separator, new String[]{"home", "user", "rdflint"});
    assertEquals("file:///home/user/rdflint",
        RdfLintLanguageServer.convertFilePath2Uri("/" + path1));

    String path2 = String.join(File.separator, new String[]{"C:", "rdflint"});
    assertEquals("file:///C%3A/rdflint",
        RdfLintLanguageServer.convertFilePath2Uri(path2));
  }

}
