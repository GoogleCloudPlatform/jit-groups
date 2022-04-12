package com.google.solutions.jitaccess.core;

import java.io.IOException;
import java.util.Properties;

public class ApplicationVersion {
  public static final String VERSION_STRING = loadVersion();

  private static String loadVersion()
  {
    try
    {
      //
      // Read properties file from JAR. This file should
      // contain the version number, produced by the Maven
      // resources plugin.
      //
      var propertiesFile = ApplicationVersion.class
          .getClassLoader()
          .getResourceAsStream("version.properties");
      if (propertiesFile != null) {
        var versionProperties = new Properties();
        versionProperties.load(propertiesFile);

        var version = versionProperties.getProperty("application.version");
        if (version != null && version.length() > 0) {
          return version;
        }
      }
    }
    catch (IOException ignored)
    {
    }

    return "unknown";
  }
}
