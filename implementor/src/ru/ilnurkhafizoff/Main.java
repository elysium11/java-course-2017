package ru.ilnurkhafizoff;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import ru.ilnurkhafizoff.implementor.Implementor;

public class Main {

  public static void main(String[] args) {
    if (args.length < 2) {
      throw new IllegalArgumentException(
          "There are full class name and result path should be specified.");
    }

    try {
      if ("-jar".equals(args[0])) {

        if (args.length < 3) {
          throw new IllegalArgumentException(
              "There are full class name and result path should be specified.");
        }

        Path jarPath = Paths.get(args[2]);
        Implementor implementor = new Implementor();
        Path tempDirectory = Files.createTempDirectory("implementor");
        implementor.implementJar(Class.forName(args[1]), tempDirectory);
        Files.move(
            implementor.getJarImplementationPath(),
            jarPath.toAbsolutePath(),
            REPLACE_EXISTING
        );

        Files.walk(tempDirectory)
            .map(Path::toFile)
            .forEach(File::deleteOnExit);
      } else {
        new Implementor().implement(Class.forName(args[0]), Paths.get(args[1]));
      }
    } catch (IOException e) {
      System.err.println("Could not write to file in specified path: " + e.getMessage());
    } catch (ImplerException e) {
      System.err.println("Could not implement specified class: " + e.getMessage());
    } catch (ClassNotFoundException e) {
      System.err.println("'" + args[0] + "' class not found");
    }
  }

}
