package com.github.imas.rdflint;

import com.github.imas.rdflint.config.RdfLintParameters;
import com.github.imas.rdflint.validator.RdfValidator;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.yaml.snakeyaml.Yaml;


public class RdfLint {

  public static final String VERSION = "0.1.2";
  private static final Logger logger = Logger.getLogger(RdfLint.class.getName());

  protected static final List<String> CONFIG_SEARCH_PATH = Collections.unmodifiableList(
      new ArrayList<String>() {
        {
          add("rdflint-config.yml");
          add(".rdflint-config.yml");
          add(".rdflint/rdflint-config.yml");
          add("config/rdflint/rdflint-config.yml");
          add(".circleci/rdflint-config.yml");
        }
      }
  );

  protected static final List<String> SUPPRESS_SEARCH_PATH = Collections.unmodifiableList(
      new ArrayList<String>() {
        {
          add("rdflint-suppress.yml");
          add(".rdflint-suppress.yml");
          add(".rdflint/rdflint-suppress.yml");
          add("config/rdflint/rdflint-suppress.yml");
          add(".circleci/rdflint-suppress.yml");
        }
      }
  );

  /**
   * rdflint entry point.
   */
  public static void main(String[] args) throws ParseException, IOException {

    // Parse CommandLine Parameter
    Options options = new Options();
    options.addOption("baseuri", true, "RDF base URI");
    options.addOption("targetdir", true, "Target Directory Path");
    options.addOption("outputdir", true, "Output Directory Path");
    options.addOption("origindir", true, "Origin Dataset Directory Path");
    options.addOption("config", true, "Configuration file Path");
    options.addOption("suppress", true, "Suppress problems file Path");
    options.addOption("i", false, "Interactive mode");
    options.addOption("ls", false, "Language Server mode (experimental)");
    options.addOption("h", false, "Print usage");
    options.addOption("v", false, "Print version");
    options.addOption("vv", false, "Verbose logging (for debugging)");

    CommandLine cmd = null;

    try {
      CommandLineParser parser = new DefaultParser();
      cmd = parser.parse(options, args);
    } catch (UnrecognizedOptionException e) {
      System.out.println("Unrecognized option: " + e.getOption()); // NOPMD
      System.exit(1);
    }

    // print version
    if (cmd.hasOption("v")) {
      System.out.println("rdflint " + VERSION); // NOPMD
      return;
    }

    // print usage
    if (cmd.hasOption("h")) {
      HelpFormatter f = new HelpFormatter();
      f.printHelp("rdflint [options]", options);
      return;
    }

    // verbose logging mode
    if (cmd.hasOption("vv")) {
      Logger.getLogger("com.github.imas.rdflint").setLevel(Level.TRACE);
    }

    // Execute Language Server Mode
    if (cmd.hasOption("ls")) {
      RdfLintLanguageServer server = new RdfLintLanguageServer();
      Launcher<LanguageClient> launcher = LSPLauncher
          .createServerLauncher(server, System.in, System.out);
      LanguageClient client = launcher.getRemoteProxy();
      server.connect(client);
      launcher.startListening();
      return;
    }

    // Set parameter
    String targetDir = cmd.getOptionValue("targetdir");
    String parentPath = targetDir != null ? targetDir : ".";
    String configPath = cmd.getOptionValue("config");
    if (configPath == null) {
      for (String fn : CONFIG_SEARCH_PATH) {
        Path path = Paths.get(parentPath + "/" + fn);
        if (Files.exists(path)) {
          configPath = path.toAbsolutePath().toString();
          break;
        }
      }
    }
    RdfLint lint = new RdfLint();
    RdfLintParameters params = lint.loadConfig(configPath);
    setupParameters(params, cmd, targetDir, parentPath);

    // Main procedure
    if (cmd.hasOption("i")) {
      // Execute Interactive mode
      InteractiveMode imode = new InteractiveMode();
      imode.execute(params, params.getTargetDir());
    } else {
      // Execute linter
      LintProblemSet problems = lint.lintRdfDataSet(params, params.getTargetDir());
      if (problems.hasProblem()) {
        Path problemsPath = Paths.get(params.getOutputDir() + "/rdflint-problems.yml");
        LintProblemFormatter.out(System.out, problems);
        LintProblemFormatter.yaml(Files.newOutputStream(problemsPath), problems);
        if (problems.hasError()) {
          System.exit(1);
        }
      }
    }
  }

  static void setupParameters(
      RdfLintParameters params, CommandLine cmd, String targetDir, String parentPath) {
    String suppressPath = cmd.getOptionValue("suppress");
    if (suppressPath == null) {
      for (String fn : SUPPRESS_SEARCH_PATH) {
        Path path = Paths.get(parentPath + "/" + fn);
        if (Files.exists(path)) {
          suppressPath = path.toAbsolutePath().toString();
          break;
        }
      }
    }

    if (targetDir != null) {
      params.setTargetDir(targetDir);
    } else if (params.getTargetDir() == null) {
      params.setTargetDir(".");
    }

    String outputDir = cmd.getOptionValue("outputdir");
    if (outputDir != null) {
      params.setOutputDir(outputDir);
    } else if (params.getOutputDir() == null) {
      params.setOutputDir(params.getTargetDir());
    }

    String baseUri = cmd.getOptionValue("baseuri");
    if (baseUri != null) {
      params.setBaseUri(baseUri);
    }

    String originPath = cmd.getOptionValue("origindir");
    if (originPath != null) {
      params.setOriginDir(originPath);
    }

    if (suppressPath != null) {
      params.setSuppressPath(suppressPath);
    }
  }

  /**
   * load configuration file.
   */
  public RdfLintParameters loadConfig(String configPath) throws IOException {
    logger.trace(String.format("loadConfig: configPath=%s", configPath));
    if (configPath == null) {
      return new RdfLintParameters();
    }
    Yaml yaml = new Yaml();
    return yaml.loadAs(
        new InputStreamReader(
            Files.newInputStream(Paths.get(new File(configPath).getCanonicalPath())),
            StandardCharsets.UTF_8),
        RdfLintParameters.class);
  }

  public void parseValidationConfiguration(
      RdfLintParameters params, RdfValidator validator, Object conf)
      throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException, InstantiationException, ClassNotFoundException {
    // detect validator name
    String clzName = validator.getClass().getSimpleName();
    if (clzName.length() > "Validator".length()) {
      clzName = clzName.substring(0, clzName.length() - "Validator".length());
    }
    String validatorName = clzName.substring(0, 1).toLowerCase() + clzName.substring(1);

    // validator params to config object
    Object paramObj = params.getValidation().get(validatorName);
    System.out.println(paramObj);
    if (paramObj instanceof Map) {
      Map map = (Map) paramObj;
      for (Object key : map.keySet()) {
        System.out.println(key);
        if (map.get(key) instanceof Map) {

        } else if (map.get(key) instanceof List) {
          BeanUtils.setProperty(conf, key.toString(), new LinkedList<>());
          for (Object e : (List) map.get(key)) {
            List lst2 = (List) PropertyUtils.getProperty(conf, key.toString());
//            System.out.println(PropertyUtils.getPropertyType(conf, key.toString() + "[0]."));
//            PropertyDescriptor pd = PropertyUtils.getPropertyDescriptor(conf, key.toString());
//            System.out.println(PropertyUtils.getPropertyDescriptor(conf, key.toString()));
//
//            System.out.println(conf.getClass());
            Field f = conf.getClass().getDeclaredField(key.toString());
//            System.out.println(f.getGenericType());
//            System.out.println(f.getGenericType().getTypeName().split("<")[1].split(">")[0]);
            String className = f.getGenericType().getTypeName().split("<")[1].split(">")[0];
//            Object o = Class.forName(className).getDeclaredConstructor(new Class[]{}).newInstance();
//            Constructor ct = Class.forName(className).getDeclaredConstructor(new Class[]{});
            Constructor[] cts = Class.forName(className).getConstructors();
            System.out.println(cts);
            Constructor ct = cts[0];
            System.out.println(ct.getParameterTypes());
            Class[] ptypes = ct.getParameterTypes();
            System.out.println(ct);
            ct.setAccessible(true);
////            Object o = ct.newInstance(validator);
//            Object o = ct.newInstance(conf);
            Object o;

            if (ptypes[0] == conf.getClass()) {
              o = ct.newInstance(conf);
            } else {
              o = ct.newInstance();
            }

            System.out.println(o.getClass());
//            System.out.println(f.getGenericType().getClass());
//            Constructor ct = f.getGenericType().getClass().getDeclaredConstructor(new Class[]{});
//            ct.setAccessible(true);
//            Object o = ct.newInstance();
            lst2.add(o);

//                Field field3 = GenericTest.class.getField("objs3");
//            System.out.println("[objs3]");
//            type = field3.getGenericType();
//            System.out.println(type + " : " + type.getClass());

//            PropertyUtils.set
//            lst2.add();
//            lst.add(null);
            if (e instanceof Map) {
              Map subMap = (Map) e;
              for (Object subKey : subMap.keySet()) {
                System.out
                    .println(String.format("%s[%d].%s", key.toString(), 0, subKey.toString()));
                System.out.println(subMap.get(subKey));
                PropertyUtils.setNestedProperty(conf,
                    String.format("%s[%d].%s", key.toString(), 0, subKey.toString()),
                    subMap.get(subKey));
//                BeanUtils.setProperty(conf,
//                    String.format("%s[%d].%s", key.toString(), 0, subKey.toString()),
//                    subMap.get(subKey));
              }
            }
          }

//          BeanUtils.setProperty(conf, key.toString(), map.get(key));

        } else {
          BeanUtils.setProperty(conf, key.toString(), map.get(key));
        }
      }
//      BeanUtils.setProperty(conf );

//      validationParamMap = makeStringMap((Map) paramObj);
//    } else if (paramObj instanceof List) {
//      for (Object obj : (List) paramObj) {
//        if (obj instanceof Map) {
//          validationParamMapList.add(makeStringMap((Map) obj));
//        }
//      }
    }
    //    v.getValidatorName();
//    String clzName = this.getClass().getSimpleName();
//    if (clzName.length() > "Validator".length()) {
//      clzName = clzName.substring(0, clzName.length() - "Validator".length());
//    }
//    validatorName = clzName.substring(0, 1).toLowerCase() + clzName.substring(1);

//    this.params = params;
//    validationParams = null;
//    if (this.params != null && this.params.getValidation() != null) {
//      validationParams = this.params.getValidation().get(this.getValidatorName());
//    }

    //    BeanUtils.setProperty();

//    Class[] cArg = new Class[1];
//    cArg[0] = Integer.class;
////    Object o = clz.getDeclaredConstructor(cArg).newInstance();
//    Constructor ct = clz.getConstructor(cArg);
//    Object o = ct.newInstance(1);
//    return null;
  }

  private Map<String, String> makeStringMap(Map paramObj) {
    Map<String, String> map = new ConcurrentHashMap<>();
    for (Object obj : ((Map) paramObj).entrySet()) {
      if (obj instanceof Map.Entry) {
        Map.Entry e = (Map.Entry) obj;
        map.put(e.getKey().toString(), e.getValue().toString());
      }
    }
    return map;
  }

  /**
   * rdflint main process.
   */
  LintProblemSet lintRdfDataSet(RdfLintParameters params, String targetDir)
      throws IOException {
    logger.trace("lintRdfDataSet: in");

    // execute generator
    GenerationRunner grunner = new GenerationRunner();
    grunner.execute(params, targetDir);

    // call validator runner
    ValidationRunner runner = new ValidationRunner();
    runner.appendRdfValidatorsFromPackage("com.github.imas.rdflint.validator.impl");
    return runner.execute(params, targetDir);
  }

}
