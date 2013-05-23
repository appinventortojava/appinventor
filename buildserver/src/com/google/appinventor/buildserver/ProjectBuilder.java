// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the MIT License https://raw.github.com/mit-cml/app-inventor/master/mitlicense.txt

package com.google.appinventor.buildserver;

import com.google.appinventor.common.utils.StringUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.google.common.io.Resources;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;

/**
 * Provides support for building Young Android projects.
 *
 * @author markf@google.com (Mark Friedman)
 */
public final class ProjectBuilder {

  private File outputApk;
  private File outputEclipseZip; 

  private File outputKeystore;
  private boolean saveKeystore;
  private String projectName = "JavaFiles.zip";

  // Logging support
  private static final Logger LOG = Logger.getLogger(ProjectBuilder.class.getName());

  private static final int MAX_COMPILER_MESSAGE_LENGTH = 160;

  // Project folder prefixes
  // TODO(user): These constants are (or should be) also defined in
  // appengine/src/com/google/appinventor/server/project/youngandroid/YoungAndroidProjectService
  // They should probably be in some place shared with the server
  private static final String PROJECT_DIRECTORY = "youngandroidproject";
  private static final String PROJECT_PROPERTIES_FILE_NAME = PROJECT_DIRECTORY + "/" +
      "project.properties";
  private static final String KEYSTORE_FILE_NAME = YoungAndroidConstants.PROJECT_KEYSTORE_LOCATION;

  private static final String FORM_PROPERTIES_EXTENSION =
      YoungAndroidConstants.FORM_PROPERTIES_EXTENSION;
  private static final String YAIL_EXTENSION = YoungAndroidConstants.YAIL_EXTENSION;

  private static final String CODEBLOCKS_SOURCE_EXTENSION =
      YoungAndroidConstants.CODEBLOCKS_SOURCE_EXTENSION;

  private static final String ALL_COMPONENT_TYPES =
      Compiler.RUNTIME_FILES_DIR + "simple_components.txt";

  private static int eclipseBuildStatus = 10;

  public static int getEclipseBuildStatus() {
    return eclipseBuildStatus;
  }

  public static void setEclipseBuildStatus(int eclipseBuildStatus) {
    ProjectBuilder.eclipseBuildStatus = eclipseBuildStatus;
  }

  public File getOutputApk() {
    return outputApk;
  }

  public File getOutputEclipseZip() {
    return outputEclipseZip;
  }

  public File getOutputKeystore() {
    return outputKeystore;
  }

  /**
   * Creates a new directory beneath the system's temporary directory (as
   * defined by the {@code java.io.tmpdir} system property), and returns its
   * name. The name of the directory will contain the current time (in millis),
   * and a random number.
   *
   * <p>This method assumes that the temporary volume is writable, has free
   * inodes and free blocks, and that it will not be called thousands of times
   * per second.
   *
   * @return the newly-created directory
   * @throws IllegalStateException if the directory could not be created
   */
  private static File createNewTempDir() {
    File baseDir = new File(System.getProperty("java.io.tmpdir"));
    String baseNamePrefix = System.currentTimeMillis() + "_" + Math.random() + "-";

    final int TEMP_DIR_ATTEMPTS = 10000;
    for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
      File tempDir = new File(baseDir, baseNamePrefix + counter);
      if (tempDir.exists()) {
        continue;
      }
      if (tempDir.mkdir()) {
        return tempDir;
      }
    }
    throw new IllegalStateException("Failed to create directory within "
        + TEMP_DIR_ATTEMPTS + " attempts (tried "
        + baseNamePrefix + "0 to " + baseNamePrefix + (TEMP_DIR_ATTEMPTS - 1) + ')');
  }

  Result build(String userName, ZipFile inputZip, File outputDir, boolean isForRepl, boolean isForWireless,
      int childProcessRam) {
    try {
      // Download project files into a temporary directory
      File projectRoot = createNewTempDir();
      LOG.info("temporary project root: " + projectRoot.getAbsolutePath());
      try {
        List<String> sourceFiles;
        try {
          sourceFiles = extractProjectFiles(inputZip, projectRoot);
        } catch (IOException e) {
          LOG.severe("unexpected problem extracting project file from zip");
          return Result.createFailingResult("", "Problems processing zip file.");
        }

        try {
          genYailFilesIfNecessary(sourceFiles);
        } catch (YailGenerationException e) {
          // Note that we're using a special result code here for the case of a Yail gen error.
          return new Result(Result.YAIL_GENERATION_ERROR, "", e.getMessage(), e.getFormName());
        } catch (Exception e) {
          LOG.severe("Unknown exception signalled by genYailFilesIf Necessary");
          e.printStackTrace();
          return Result.createFailingResult("", "Unexpected problems generating YAIL.");
        }

        File keyStoreFile = new File(projectRoot, KEYSTORE_FILE_NAME);
        String keyStorePath = keyStoreFile.getPath();
        if (!keyStoreFile.exists()) {
          keyStorePath = createKeyStore(userName, projectRoot, KEYSTORE_FILE_NAME);
          saveKeystore = true;
        }

        // Create project object from project properties file.
        Project project = getProjectProperties(projectRoot);

        File buildTmpDir = new File(projectRoot, "build/tmp");
        buildTmpDir.mkdirs();

        // Prepare for redirection of compiler message output
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream console = new PrintStream(output);
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream userErrors = new PrintStream(errors);

        Set<String> componentTypes =
            (isForRepl || isForWireless) ? getAllComponentTypes() : getComponentTypes(sourceFiles);

            // Invoke YoungAndroid compiler
            boolean success =
                Compiler.compile(project, componentTypes, console, console, userErrors, isForRepl, isForWireless,
                    keyStorePath, childProcessRam);
            console.close();
            userErrors.close();

            // Retrieve compiler messages and convert to HTML and log
            String srcPath = projectRoot.getAbsolutePath() + "/" + PROJECT_DIRECTORY + "/../src/";
            String messages = processCompilerOutput(output.toString(PathUtil.DEFAULT_CHARSET),
                srcPath);

            if (success) {
              // Locate output file
              File outputFile = new File(projectRoot,
                  "build/deploy/" + project.getProjectName() + ".apk");
              if (!outputFile.exists()) {
                LOG.warning("Young Android build - " + outputFile + " does not exist");
              } else {
                outputApk = new File(outputDir, outputFile.getName());
                Files.copy(outputFile, outputApk);
                if (saveKeystore) {
                  outputKeystore = new File(outputDir, KEYSTORE_FILE_NAME);
                  Files.copy(keyStoreFile, outputKeystore);
                }
              }
            }
            return new Result(success, messages, errors.toString(PathUtil.DEFAULT_CHARSET));
      } finally {
        // On some platforms (OS/X), the java.io.tmpdir contains a symlink. We need to use the
        // canonical path here so that Files.deleteRecursively will work.

        // Note (ralph):  deleteRecursively has been removed from the guava-11.0.1 lib
        // Replacing with deleteDirectory, which is supposed to delete the entire directory.
        FileUtils.deleteDirectory(new File(projectRoot.getCanonicalPath()));
      }
    } catch (Exception e) {
      e.printStackTrace();
      return Result.createFailingResult("", "Server error performing build");
    }
  }

  Result buildEclipseProject(String userName, File inputZipFile, File outputDir, int buildOption) {
    try {
      // Download project files into a temporary directory
      File projectRoot = createNewTempDir();
      LOG.info("temporary project root: " + projectRoot.getAbsolutePath());
      try {

        // Create placeholders for output and errors 
        StringBuffer outputStringBuffer = new StringBuffer();
        StringBuffer errorStringBuffer = new StringBuffer();
        File tmpProjectOutputDir = null;
        File tmpProjectOutputDir2 = null;
        ArrayList<File> yailFiles = null;

        // builds the ecplise project
        if(buildOption == 1) {
          LOG.info("******* entering build for project!! *******");
          List<String> sourceFiles;

          setEclipseBuildStatus(20);

          // Extracts the files given to us from app inventor
          sourceFiles = extractProjectFiles(new ZipFile(inputZipFile), projectRoot);

          setEclipseBuildStatus(30);
          try {
            LOG.info("******* gen yail files for project!! *******");
            yailFiles = genYailFilesIfNecessary(sourceFiles);
          } catch (YailGenerationException e) {
            // Note that we're using a special result code here for the case of a Yail gen error.
            return new Result(Result.YAIL_GENERATION_ERROR, "", e.getMessage(), e.getFormName());
          } catch (Exception e) {
            LOG.severe("Unknown exception signalled by genYailFilesIf Necessary");
            e.printStackTrace();
            return Result.createFailingResult("", "Unexpected problems generating YAIL.");
          } 

          setEclipseBuildStatus(60);
          try {
            tmpProjectOutputDir2 = genJavaSources(yailFiles, projectRoot, outputStringBuffer, errorStringBuffer, "eclipse");
          } catch (IOException e) {
            return new Result(Result.YAIL_TO_JAVA_GENERATION_ERROR, "",
                e.getMessage(), "- Not Applicable -");
          } catch (Exception e) {
            LOG.severe("Unknown exception signalled by buildEclipseProject with 'build Java sources only' option");
            e.printStackTrace();
            return Result.createFailingResult("",
                "Unexpected problems generating Java sources out of AppInvertor source");
          }
          setEclipseBuildStatus(80);

          extractAssets(new ZipFile(inputZipFile), tmpProjectOutputDir2);

          setEclipseBuildStatus(100);
          outputEclipseZip = new File(outputDir, projectName);
          ZipOutputStream zipOS = new ZipOutputStream(new FileOutputStream(outputEclipseZip));
          ZipUtil.recursiveZip(tmpProjectOutputDir2,tmpProjectOutputDir2.toURI(),zipOS);

          zipOS.flush();
          zipOS.close();
        } 
        // generats only java files 
        else {
          List<String> sourceFiles;

          setEclipseBuildStatus(20);
          try {
            sourceFiles = extractProjectFiles(new ZipFile(inputZipFile), projectRoot);
          } catch (IOException e) {
            LOG.severe("unexpected problem extracting project file from zip");
            return Result.createFailingResult("", "Problems processing zip file.");
          }

          setEclipseBuildStatus(30);
          try {
            yailFiles = genYailFilesIfNecessary(sourceFiles);
          } catch (YailGenerationException e) {
            // Note that we're using a special result code here for the case of a Yail gen error.
            return new Result(Result.YAIL_GENERATION_ERROR, "", e.getMessage(), e.getFormName());
          } catch (Exception e) {
            LOG.severe("Unknown exception signalled by genYailFilesIf Necessary");
            e.printStackTrace();
            return Result.createFailingResult("", "Unexpected problems generating YAIL.");
          } 

          setEclipseBuildStatus(60);
          try {
            tmpProjectOutputDir = genJavaSources(yailFiles, projectRoot, outputStringBuffer, errorStringBuffer, "source");
          } catch (IOException e) {
            return new Result(Result.YAIL_TO_JAVA_GENERATION_ERROR, "",
                e.getMessage(), "- Not Applicable -");
          } catch (Exception e) {
            LOG.severe("Unknown exception signalled by buildEclipseProject with 'build Java sources only' option");
            e.printStackTrace();
            return Result.createFailingResult("",
                "Unexpected problems generating Java sources out of AppInvertor source");
          }
          setEclipseBuildStatus(80);
          outputEclipseZip = new File(outputDir, projectName);

          setEclipseBuildStatus(100);

          if(tmpProjectOutputDir.isDirectory() && tmpProjectOutputDir.exists()) {
            ZipOutputStream zipOS = new ZipOutputStream(new FileOutputStream(outputEclipseZip));
            ZipUtil.recursiveZip(tmpProjectOutputDir,tmpProjectOutputDir.toURI(),zipOS);
            zipOS.flush();
            zipOS.close();
          }
        }

        return new Result(true, outputStringBuffer.toString(),errorStringBuffer.toString());
      } finally {
        // ********** make sure to uncomment later!!! 

        FileUtils.deleteDirectory(new File(projectRoot.getCanonicalPath()));
      }
    } catch (Exception e) {
      e.printStackTrace();
      return Result.createFailingResult("","Server error performing Eclipse Project build");
    }
  }

// Outputs the path with either the eclipse project or java files deppending on the mode
  
  private File genJavaSources(ArrayList<File> yailFiles, File projectRoot,
      StringBuffer outputStringBuffer, StringBuffer errorStringBuffer, String mode) throws IOException,
      YailToJavaGenerationException {

    File tmpJavaFolder = new File(projectRoot + File.separator +"java");
    File tmpJavaFolder2 = null;
    tmpJavaFolder.deleteOnExit(); 
    tmpJavaFolder.mkdir();

    StringBuffer out = new StringBuffer();
    StringBuffer err = new StringBuffer();

    File yailFile = yailFiles.iterator().next();
    System.out.println("\n\n\n" + yailFile.getParent());
    tmpJavaFolder2 = new File(yailFile.getParent());
    tmpJavaFolder2 = new File(tmpJavaFolder2.getParent());
    StringBuffer innerOut = new StringBuffer();
    StringBuffer innerErr = new StringBuffer();
    String[] commandLine = {
        System.getProperty("java.home") + "/bin/java",
        "-mx1024M",
        "-jar",
        Compiler.getResource(Compiler.RUNTIME_FILES_DIR
            + "yail-to-java.jar"),
            yailFile.getParent() ,
            mode
    };

    int exitValue = Execution.execute(null, commandLine, innerOut, innerErr);

    if (exitValue != 0) {
      errorStringBuffer = errorStringBuffer.append(innerErr);
      throw new YailToJavaGenerationException(innerErr.toString());
    }  
    else {
      // extract the project name for stdout from the java generator
      String[] projectNametmp = innerOut.toString().split("\n");
      projectName = projectNametmp[projectNametmp.length - 1]+ ".zip";
    }   

    try {
      File dir = new File(yailFile.getParent());
      FileUtils.deleteDirectory(dir);
    } catch(Exception e){
      e.printStackTrace();
    }

    out = out.append(innerOut);
    err = err.append(innerErr);

    return tmpJavaFolder2;
  }

  // not used any more but keep for possible furture use / reference 
  private void genEclipseProject(File inputZipFile, File projectRoot,
      StringBuffer outputStringBuffer, StringBuffer errorStringBuffer) throws IOException,
      EclipseProjectGenerationException {
    // Copy the input file to a temporary file
    File tmpInputZipFile = File.createTempFile(inputZipFile.getName(),
        ".zip", projectRoot);
    tmpInputZipFile.deleteOnExit();
    Files.copy(inputZipFile, tmpInputZipFile);

    // Create the output folder
    File tmpOutputFolder = new File(projectRoot + File.separator + "output");
    tmpOutputFolder.deleteOnExit();
    tmpOutputFolder.mkdir();

    String[] commandLine = {
        System.getProperty("java.home") + "/bin/java",
        "-mx1024M",
        "-cp",
        Compiler.getResource(Compiler.RUNTIME_FILES_DIR
            + "app-inventor-to-eclipse-project.jar"),
            "AppInventorToEclipseProject",
            tmpInputZipFile.getAbsolutePath(),
            tmpOutputFolder.getAbsolutePath() };

    int exitValue = Execution.execute(null, commandLine, outputStringBuffer, errorStringBuffer);

    if (exitValue != 0)
      throw new EclipseProjectGenerationException(errorStringBuffer.toString());

    FileUtils.deleteDirectory(new File(projectRoot + File.separator + "output" + File.separator + "src"));
  }

  private ArrayList<File> genYailFilesIfNecessary(List<String> sourceFiles)
      throws IOException, YailGenerationException {
    ArrayList<File> yailFiles = new ArrayList<File>();  
    // Filter out the files that aren't really source files (i.e. that don't end in .scm or .yail)
    Collection<String> formAndYailSourceFiles = Collections2.filter(
        sourceFiles,
        new Predicate<String>() {
          @Override
          public boolean apply(String input) {
            return input.endsWith(FORM_PROPERTIES_EXTENSION) || input.endsWith(YAIL_EXTENSION);
          }
        });
    for (String sourceFile : formAndYailSourceFiles) {
      if (sourceFile.endsWith(FORM_PROPERTIES_EXTENSION)) {
        String rootPath = sourceFile.substring(0, sourceFile.length()
            - FORM_PROPERTIES_EXTENSION.length());
        String yailFilePath = rootPath + YAIL_EXTENSION;
        // Note: Famous last words: The following contains() makes this method O(n**2) but n should
        // be pretty small.
        if (!sourceFiles.contains(yailFilePath)) {
          yailFiles.add(generateYail(rootPath));
        }
      }
    }
    return yailFiles;
  }

  private static Set<String> getAllComponentTypes() throws IOException {
    Set<String> compSet = Sets.newHashSet();
    String[] components = Resources.toString(
        ProjectBuilder.class.getResource(ALL_COMPONENT_TYPES), Charsets.UTF_8).split("\n");
    for (String component : components) {
      compSet.add(component);
    }
    return compSet;
  }

  private ArrayList<String> extractProjectFiles(ZipFile inputZip, File projectRoot)
      throws IOException {
    ArrayList<String> projectFileNames = Lists.newArrayList();
    Enumeration<? extends ZipEntry> inputZipEnumeration = inputZip.entries();
    while (inputZipEnumeration.hasMoreElements()) {
      ZipEntry zipEntry = inputZipEnumeration.nextElement();
      final InputStream extractedInputStream = inputZip.getInputStream(zipEntry);
      File extractedFile = new File(projectRoot, zipEntry.getName());
      LOG.info("extracting " + extractedFile.getAbsolutePath() + " from input zip");
      Files.createParentDirs(extractedFile); // Do I need this?
      Files.copy(
          new InputSupplier<InputStream>() {
            public InputStream getInput() throws IOException {
              return extractedInputStream;
            }
          },
          extractedFile);
      projectFileNames.add(extractedFile.getPath());
    }
    return projectFileNames;
  }

  private void extractAssets(ZipFile inputZip, File assetsDir)
      throws IOException {
    ArrayList<String> projectFileNames = Lists.newArrayList();
    Enumeration<? extends ZipEntry> inputZipEnumeration = inputZip.entries();
    String name;
    while (inputZipEnumeration.hasMoreElements()) {
      ZipEntry zipEntry = inputZipEnumeration.nextElement();
      final InputStream extractedInputStream = inputZip.getInputStream(zipEntry);
      name = zipEntry.getName();
      if (name.startsWith("assets")) {
        File extractedFile = new File(assetsDir.getAbsoluteFile().toString() +
            File.separator + name);
        LOG.info("extracting " + extractedFile.getAbsolutePath() + " from input zip");
        Files.createParentDirs(extractedFile); // Do I need this?
        Files.copy(
            new InputSupplier<InputStream>() {
              public InputStream getInput() throws IOException {
                return extractedInputStream;
              }
            },
            extractedFile);
      }
    }
  }

  private static Set<String> getComponentTypes(List<String> files)
      throws IOException {
    Set<String> componentTypes = Sets.newHashSet();
    for (String f : files) {
      if (f.endsWith(".scm")) {
        File scmFile = new File(f);
        String scmContent = new String(Files.toByteArray(scmFile), PathUtil.DEFAULT_CHARSET);
        componentTypes.addAll(getTypesFromScm(scmContent));
      }
    }
    return componentTypes;
  }

  static String createKeyStore(String userName, File projectRoot, String keystoreFileName)
      throws IOException {
    File keyStoreFile = new File(projectRoot.getPath(), keystoreFileName);

    /* Note: must expire after October 22, 2033, to be in the Android
     * marketplace.  Android docs recommend "10000" as the expiration # of
     * days.
     *
     * For DNAME, US may not the right country to assign it to.
     */
    String[] keytoolCommandline = {
        System.getProperty("java.home") + "/bin/keytool",
        "-genkey",
        "-keystore", keyStoreFile.getAbsolutePath(),
        "-alias", "AndroidKey",
        "-keyalg", "RSA",
        "-dname", "CN=" + quotifyUserName(userName) + ", O=AppInventor for Android, C=US",
        "-validity", "10000",
        "-storepass", "android",
        "-keypass", "android"
    };

    if (Execution.execute(null, keytoolCommandline, System.out, System.err)) {
      if (keyStoreFile.length() > 0) {
        return keyStoreFile.getAbsolutePath();
      }
    }
    return null;
  }

  @VisibleForTesting
  static Set<String> getTypesFromScm(String scm) {
    return FormPropertiesAnalyzer.getComponentTypesFromFormFile(scm);
  }

  @VisibleForTesting
  static String processCompilerOutput(String output, String srcPath) {
    // First, remove references to the temp source directory from the messages.
    String messages = output.replace(srcPath, "");

    // Then, format warnings and errors nicely.
    try {
      // Split the messages by \n and process each line separately.
      String[] lines = messages.split("\n");
      Pattern pattern = Pattern.compile("(.*?):(\\d+):\\d+: (error|warning)?:? ?(.*?)");
      StringBuilder sb = new StringBuilder();
      boolean skippedErrorOrWarning = false;
      for (String line : lines) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.matches()) {
          // Determine whether it is an error or warning.
          String kind;
          String spanClass;
          // Scanner messages do not contain either 'error' or 'warning'.
          // I treat them as errors because they prevent compilation.
          if ("warning".equals(matcher.group(3))) {
            kind = "WARNING";
            spanClass = "compiler-WarningMarker";
          } else {
            kind = "ERROR";
            spanClass = "compiler-ErrorMarker";
          }

          // Extract the filename, lineNumber, and message.
          String filename = matcher.group(1);
          String lineNumber = matcher.group(2);
          String text = matcher.group(4);

          // If the error/warning is in a yail file, generate a div and append it to the
          // StringBuilder.
          if (filename.endsWith(YoungAndroidConstants.YAIL_EXTENSION)) {
            skippedErrorOrWarning = false;
            sb.append("<div><span class='" + spanClass + "'>" + kind + "</span>: " +
                StringUtils.escape(filename) + " line " + lineNumber + ": " +
                StringUtils.escape(text) + "</div>");
          } else {
            // The error/warning is in runtime.scm. Don't append it to the StringBuilder.
            skippedErrorOrWarning = true;
          }

          // Log the message, first truncating it if it is too long.
          if (text.length() > MAX_COMPILER_MESSAGE_LENGTH) {
            text = text.substring(0, MAX_COMPILER_MESSAGE_LENGTH);
          }
        } else {
          // The line isn't an error or a warning. This is expected.
          // If the line begins with two spaces, it is a continuation of the previous
          // error/warning.
          if (line.startsWith("  ")) {
            // If we didn't skip the most recent error/warning, append the line to our
            // StringBuilder.
            if (!skippedErrorOrWarning) {
              sb.append(StringUtils.escape(line)).append("<br>");
            }
          } else {
            skippedErrorOrWarning = false;
            // We just append the line to our StringBuilder.
            sb.append(StringUtils.escape(line)).append("<br>");
          }
        }
      }
      messages = sb.toString();
    } catch (Exception e) {
      // Report exceptions that happen during the processing of output, but don't make the
      // whole build fail.
      e.printStackTrace();

      // We were not able to process the output, so we just escape for HTML.
      messages = StringUtils.escape(messages);
    }

    return messages;
  }

  /*
   * Adds quotes around the given userName and encodes embedded quotes as \".
   */
  private static String quotifyUserName(String userName) {
    Preconditions.checkNotNull(userName);
    int length = userName.length();
    StringBuilder sb = new StringBuilder(length + 2);
    sb.append('"');
    for (int i = 0; i < length; i++) {
      char ch = userName.charAt(i);
      if (ch == '"') {
        sb.append('\\').append(ch);
      } else {
        sb.append(ch);
      }
    }
    sb.append('"');
    return sb.toString();
  }

  /*
   * Loads the project properties file of a Young Android project.
   */
  private Project getProjectProperties(File projectRoot) {
    return new Project(projectRoot.getAbsolutePath() + "/" + PROJECT_PROPERTIES_FILE_NAME);
  }

  private File generateYail(String rootName) throws IOException, YailGenerationException {
    String formPropertiesPath = rootName + FORM_PROPERTIES_EXTENSION;
    String codeblocksSourcePath = rootName + CODEBLOCKS_SOURCE_EXTENSION;
    String yailPath = rootName + YAIL_EXTENSION;

    String[] commandLine = {
        System.getProperty("java.home") + "/bin/java",
        "-mx1024M",
        "-jar",
        Compiler.getResource(Compiler.RUNTIME_FILES_DIR + "YailGenerator.jar"),
        new File(formPropertiesPath).getAbsolutePath(),
        new File(codeblocksSourcePath).getAbsolutePath(),
        yailPath
    };
    StringBuffer out = new StringBuffer();
    StringBuffer err = new StringBuffer();
    int exitValue = Execution.execute(null, commandLine, out, err);
    if (exitValue == 0) {
      String generatedYailString = out.toString();
      File generatedYailFile = new File(yailPath);
      Files.write(generatedYailString, generatedYailFile, Charsets.UTF_8);
      return generatedYailFile;
    } else {
      String formName = PathUtil.trimOffExtension(PathUtil.basename(formPropertiesPath));
      if (exitValue == 1) {
        // Failed to generate yail for legitimate reasons, such as empty sockets.
        throw new YailGenerationException("Unable to generate code for " + formName + "."
            + "\n -- err is " + err.toString()
            + "\n -- out is" + out.toString(),
            formName);
      } else {
        // Any other exit value is unexpected.
        throw new RuntimeException("YailGenerator for form " + formName
            + " exited with code " + exitValue
            + "\n -- err is " + err.toString()
            + "\n -- out is" + out.toString());
      }
    }
  }

  private static class YailGenerationException extends Exception {
    // The name of the form being built when an error occurred
    private final String formName;

    YailGenerationException(String message, String formName) {
      super(message);
      this.formName = formName;
    }

    /**
     * Return the name of the form that Yail generation failed on.
     */
    String getFormName() {
      return formName;
    }
  }

  private static class EclipseProjectGenerationException extends Exception {
    EclipseProjectGenerationException(String message) {
      super(message);
    }
  }

  private static class YailToJavaGenerationException extends Exception {
    YailToJavaGenerationException(String message) {
      super(message);
    }
  }

  public int getProgress() {
    return Compiler.getProgress();
  }
}



