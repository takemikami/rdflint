package com.github.imas.rdflint;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.imas.rdflint.config.RdfLintParameters;
import com.github.imas.rdflint.validator.AbstractRdfValidator;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.junit.Test;

public class RdfLintTest {

  public String getParentPath(String testSet) {
    return this.getClass().getClassLoader().getResource("testRDFs/" + testSet).getPath();
  }

  @Test
  public void loadConfigOk() throws Exception {
    RdfLint lint = new RdfLint();
    RdfLintParameters params = lint.loadConfig(getParentPath("config/rdflint-config-ok.yml"));

    assertEquals("https://sparql.crssnky.xyz/imasrdf/", params.getBaseUri());
    assertEquals("valid.rdf", params.getRules().get(0).getTarget());
  }

  @Test
  public void loadConfigValidationMap() throws Exception {
    RdfLint lint = new RdfLint();
    RdfLintParameters params = lint
        .loadConfig(getParentPath("config/rdflint-config-validation-map.yml"));
    MapValidator validator = new MapValidator();
    MapValidator.MapValidatorConfiguration conf = validator.makeConfiguration();
    lint.parseValidationConfiguration(params, validator, conf);

    assertEquals("https://sparql.crssnky.xyz/imasrdf/", params.getBaseUri());
    assertEquals("https://www.w3.org/2002/07/owl", conf.getUrl());
    assertEquals("http://www.w3.org/2002/07/owl#", conf.getStartswith());
    assertEquals("turtle", conf.getLangtype());
  }

  public class MapValidator extends AbstractRdfValidator {

    public MapValidatorConfiguration makeConfiguration() {
      return new MapValidatorConfiguration();
    }

    public class MapValidatorConfiguration {

      String url;
      String startswith;
      String langtype;

      public void setUrl(String url) {
        this.url = url;
      }

      public String getUrl() {
        return this.url;
      }

      public void setStartswith(String startswith) {
        this.startswith = startswith;
      }

      public String getStartswith() {
        return this.startswith;
      }

      public void setLangtype(String langtype) {
        this.langtype = langtype;
      }

      public String getLangtype() {
        return this.langtype;
      }
    }
  }

  @Test
  public void loadConfigValidationMapList() throws Exception {
    RdfLint lint = new RdfLint();
    RdfLintParameters params = lint
        .loadConfig(getParentPath("config/rdflint-config-validation-maplist.yml"));
    MapListValidator validator = new MapListValidator();
    MapListValidator.MapListValidatorConfiguration conf = validator.makeConfiguration();
    lint.parseValidationConfiguration(params, validator, conf);

    assertEquals("https://sparql.crssnky.xyz/imasrdf/", params.getBaseUri());
    assertEquals("true", conf.getEnable());
    assertEquals(1, conf.getPrefixes().size());
    assertEquals("https://www.w3.org/2002/07/owl", conf.getPrefixes().get(0).getUrl());
    assertEquals("http://www.w3.org/2002/07/owl#", conf.getPrefixes().get(0).getStartswith());
    assertEquals("turtle", conf.getPrefixes().get(0).getLangtype());
  }

  public class MapListValidator extends AbstractRdfValidator {

    public MapListValidatorConfiguration makeConfiguration() {
      return new MapListValidatorConfiguration();
    }

    public class MapListValidatorConfiguration {

      String enable;
      List<Prefix> prefixes;

      public void setEnable(String enable) {
        this.enable = enable;
      }

      public String getEnable() {
        return this.enable;
      }

      public void setPrefixes(List<Prefix> prefixes) {
        this.prefixes = prefixes;
      }

      public List<Prefix> getPrefixes() {
        return prefixes;
      }

      public class Prefix {

        String url;
        String startswith;
        String langtype;

        public void setUrl(String url) {
          this.url = url;
        }

        public String getUrl() {
          return this.url;
        }

        public void setStartswith(String startswith) {
          this.startswith = startswith;
        }

        public String getStartswith() {
          return this.startswith;
        }

        public void setLangtype(String langtype) {
          this.langtype = langtype;
        }

        public String getLangtype() {
          return this.langtype;
        }
      }

    }

  }


  @Test
  public void degradeCheckOk() throws Exception {
    RdfLintParameters params = new RdfLintParameters();
    params.setBaseUri("https://sparql.crssnky.xyz/imasrdf/");
    params.setOriginDir(getParentPath("validxml"));

    RdfLint lint = new RdfLint();
    LintProblemSet problems = lint.lintRdfDataSet(params, getParentPath("originxml"));
    LintProblemFormatter.out(System.out, problems);

    assertEquals(0, problems.problemSize());
  }

  @Test
  public void degradeCheckNg() throws Exception {
    RdfLintParameters params = new RdfLintParameters();
    params.setBaseUri("https://sparql.crssnky.xyz/imasrdf/");
    params.setOriginDir(getParentPath("originxml"));

    RdfLint lint = new RdfLint();
    LintProblemSet problems = lint.lintRdfDataSet(params, getParentPath("validxml"));
    LintProblemFormatter.out(System.out, problems);

    assertEquals(1, problems.problemSize());
  }

  @Test
  public void generationRuleOk() throws Exception {
    RdfLint lint = new RdfLint();
    RdfLintParameters params = lint.loadConfig(getParentPath("config_genok/rdflint-config.yml"));

    LintProblemSet problems = lint.lintRdfDataSet(params, getParentPath("config_genok"));
    LintProblemFormatter.out(System.out, problems);

    assertEquals(1, problems.problemSize());
  }

  @Test
  public void setupParametersFromCmdOption() throws Exception {
    CommandLine cmd = mock(CommandLine.class);
    when(cmd.getOptionValue("outputdir")).thenReturn("path/outputdir");
    when(cmd.getOptionValue("baseuri")).thenReturn("http://example.com/base#");
    when(cmd.getOptionValue("origindir")).thenReturn("path/origindir");

    RdfLintParameters params = new RdfLintParameters();

    RdfLint.setupParameters(params, cmd, "path/targetdir", "path/parentdir");

    assertEquals("getTargetDir", "path/targetdir", params.getTargetDir());
    assertEquals("getOutputDir", "path/outputdir", params.getOutputDir());
    assertEquals("getBaseUri", "http://example.com/base#", params.getBaseUri());
    assertEquals("getOriginDir", "path/origindir", params.getOriginDir());
  }

  @Test
  public void setupParametersDefault() throws Exception {
    CommandLine cmd = mock(CommandLine.class);

    RdfLintParameters params = new RdfLintParameters();

    RdfLint.setupParameters(params, cmd, null, "path/parentdir");

    assertEquals("getTargetDir", ".", params.getTargetDir());
    assertEquals("getOutputDir", ".", params.getOutputDir());
    assertNull("getBaseUri", params.getBaseUri());
    assertNull("getOriginDir", params.getOriginDir());
  }


}
