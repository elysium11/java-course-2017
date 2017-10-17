package ru.ilnurkhafizoff.implementor;

import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isNative;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class Implementor implements Impler, JarImpler {

  private Path implementationPath;

  private static final Comparator<? super Method> METHOD_SIGNATURE_COMPARATOR =
      Comparator.comparing(m -> m.getName() + Arrays.toString(m.getParameterTypes()));

  private static final Map<Class, String> DEFAULT_VALUES_FOR_PRIMITIVES = new HashMap<>();

  static {
    DEFAULT_VALUES_FOR_PRIMITIVES.put(Boolean.TYPE, "false");
    DEFAULT_VALUES_FOR_PRIMITIVES.put(Byte.TYPE, "0");
    DEFAULT_VALUES_FOR_PRIMITIVES.put(Short.TYPE, "0");
    DEFAULT_VALUES_FOR_PRIMITIVES.put(Character.TYPE, "\' \'");
    DEFAULT_VALUES_FOR_PRIMITIVES.put(Integer.TYPE, "0");
    DEFAULT_VALUES_FOR_PRIMITIVES.put(Long.TYPE, "0");
    DEFAULT_VALUES_FOR_PRIMITIVES.put(Float.TYPE, "0");
    DEFAULT_VALUES_FOR_PRIMITIVES.put(Double.TYPE, "0.0");
    DEFAULT_VALUES_FOR_PRIMITIVES.put(Void.TYPE, "");
  }

  @Override
  public void implement(Class<?> aClass, Path path) throws ImplerException {
    String implementationName = aClass.getSimpleName() + "Impl";

    String implementation = generateImplementation(aClass, implementationName);

    try {
      Path packagePath = Paths.get(aClass.getPackage().getName().replace(".", "/"));
      implementationPath = path.resolve(packagePath).resolve(implementationName + ".java");

      Files.createDirectories(implementationPath.getParent());

      Files.write(
          implementationPath.toAbsolutePath(),
          implementation.getBytes(UTF_8),
          CREATE,
          TRUNCATE_EXISTING
      );
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String generateImplementation(Class<?> parent, String implementationName)
      throws ImplerException {
    if (parent.isPrimitive() || parent.isEnum() || parent.equals(Enum.class) || parent.isArray() || isFinal(
        parent.getModifiers())) {
      throw new ImplerException(
          "Implementing class should not be primitive, array, enum or final.");
    }

    String packageDeclaration = composePackageDeclaration(parent);

    String classDeclaration = composeClassDeclaration(parent, implementationName);

    String constructorsImplementation = getConstructorsImplementation(parent.getDeclaredConstructors(),
        implementationName);

    String methodsImplementation = getMethodsImplementation(parent);

    return new StringJoiner(lineSeparator())
        .add(packageDeclaration)
        .add(classDeclaration)
        .add(constructorsImplementation)
        .add(methodsImplementation)
        .add("}")
        .toString();
  }

  private String composePackageDeclaration(Class<?> parent) {
    return format("package %s;", parent.getPackage().getName());
  }


  private String composeClassDeclaration(Class<?> parent, String className) {
    String inheritanceWord = parent.isInterface() ? "implements" : "extends";
    return new StringJoiner(" ")
        .add("public class")
        .add(className)
        .add(inheritanceWord)
        .add(parent.getCanonicalName())
        .add("{")
        .toString();
  }

  private String getConstructorsImplementation(Constructor<?>[] constructors, String className)
      throws ImplerException {
    if (constructors.length == 0 || stream(constructors).anyMatch(c -> !isPrivate(c.getModifiers()))) {
      return stream(constructors)
          .filter(c -> !isPrivate(c.getModifiers()))
          .map(c -> composeConstructorImplementation(c, className))
          .collect(joining(lineSeparator() + lineSeparator()));
    }

    throw new ImplerException("Could not implement class with only private constructors.");
  }

  private String composeConstructorImplementation(Constructor<?> constructor, String className) {
    Parameter[] parameters = constructor.getParameters();

    String accessModifier = getAccessModifier(constructor);

    String parametersDeclaration = getParametersDeclaration(parameters);

    String throwsDeclaration = getThrowsDeclaration(constructor);

    String constructorDeclaration =
        "  " + accessModifier + " " + className + parametersDeclaration + " " + throwsDeclaration
            + " {";

    String body = stream(parameters)
        .map(Parameter::getName)
        .collect(joining(", ", "    super(", ");"));

    return new StringJoiner(lineSeparator())
        .add(constructorDeclaration)
        .add(body)
        .add("  }")
        .toString();
  }

  private String getParametersDeclaration(Parameter[] parameters) {
    return stream(parameters)
        .map(p -> getParameterType(p) + " " + p.getName())
        .collect(joining(", ", "(", ")"));
  }

  private String getThrowsDeclaration(Executable executable) {
    Class<?>[] exceptionTypes = executable.getExceptionTypes();

    if (exceptionTypes.length == 0) {
      return "";
    }

    return stream(exceptionTypes)
        .map(Class::getCanonicalName)
        .collect(joining(", ", "throws ", ""));
  }

  private String getParameterType(Parameter parameter) {
    if (parameter.isVarArgs()) {
      return parameter.getType().getComponentType().getCanonicalName() + "...";
    } else {
      return handleType(parameter.getType());
    }
  }

  private String handleType(Class<?> type) {
    if (type.isArray()) {
      return type.getComponentType().getCanonicalName() + "[]";
    } else {
      return type.getCanonicalName();
    }
  }

  private String getMethodsImplementation(Class<?> parent) {
    Collection<Method> notImplementedMethods = extractOnlyNotImplementedMethods(parent);

    return notImplementedMethods.stream()
        .map(this::composeMethodImplementation)
        .collect(joining(lineSeparator()));
  }

  private Collection<Method> extractOnlyNotImplementedMethods(Class<?> parent) {
    Set<Method> implementedMethods = new TreeSet<>(METHOD_SIGNATURE_COMPARATOR);
    Set<Method> allMethods = new TreeSet<>(METHOD_SIGNATURE_COMPARATOR);

    Class<?> superClass = parent;
    while (superClass != null) {
      List<Method> superClassMethods = stream(superClass.getDeclaredMethods())
          .filter(this::isImplementable)
          .collect(toList());
      allMethods.addAll(superClassMethods);

      List<Method> implementedInSuperClass = superClassMethods.stream()
          .filter(m -> !isAbstract(m.getModifiers()))
          .collect(toList());
      implementedMethods.addAll(implementedInSuperClass);

      List<Method> interfacesMethods = stream(superClass.getInterfaces())
          .flatMap(i -> stream(i.getMethods()))
          .filter(m -> !m.isDefault())
          .collect(toList());
      allMethods.addAll(interfacesMethods);

      superClass = superClass.getSuperclass();
    }

    allMethods.removeAll(implementedMethods);
    return allMethods;
  }

  private boolean isImplementable(Method method) {
    int modifiers = method.getModifiers();
    return !isPrivate(modifiers) && !isStatic(modifiers) && !isNative(modifiers);
  }

  private String composeMethodImplementation(Method method) {
    String accessModifier = getAccessModifier(method);

    String parametersDeclaration = getParametersDeclaration(method.getParameters());

    Class<?> returnType = method.getReturnType();
    String returnTypeDeclaration = handleType(returnType);

    String methodDeclaration = new StringJoiner(" ")
        .add("  " + accessModifier)
        .add(returnTypeDeclaration)
        .add(method.getName() + parametersDeclaration)
        .add("{")
        .toString();
//        "  " + accessModifier + " " + " " + method.getName() + parametersDeclaration + " {";

    String body = "    return ";
    if (returnType.isPrimitive()) {
      body += DEFAULT_VALUES_FOR_PRIMITIVES.get(returnType) + ";";
    } else {
      body += "null;";
    }

    return new StringJoiner(lineSeparator())
        .add(methodDeclaration)
        .add(body)
        .add("  }")
        .toString();
  }

  private String getAccessModifier(Executable executable) {
    int modifiers = executable.getModifiers();
    if (isPublic(modifiers)) {
      return "public";
    }
    if (isProtected(modifiers)) {
      return "protected";
    }
    if (isPrivate(modifiers)) {
      return "private";
    }
    return ""; // default access modifier
  }

  @Override
  public void implementJar(Class<?> aClass, Path path) throws ImplerException {
    Path compiledClassesDir = path.getParent().resolve("classes");

    implement(aClass, compiledClassesDir);
    compileJavaFile(implementationPath);

    try {
      deleteSourceFile(implementationPath);
      archiveToJar(compiledClassesDir, path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void compileJavaFile(Path file) {
    JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
    javaCompiler.run(null, null, null, file.toString());
  }

  private void deleteSourceFile(Path file) throws IOException {
    Files.deleteIfExists(file);
  }

  private void archiveToJar(Path compiledClassesDir, Path jarPath) throws IOException {
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
      Files.walk(compiledClassesDir)
          .forEach(p -> writeToJar(jarOutputStream, p));
    }
  }

  private void writeToJar(JarOutputStream jarOutputStream, Path file) {
    if (Files.isDirectory(file)) {
      JarEntry jarEntry = new JarEntry(file.getFileName() + "/");
      try {
        jarOutputStream.putNextEntry(jarEntry);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      try (InputStream in = Files.newInputStream(file);) {
        byte[] bytes = new byte[4096];
        int count = in.read(bytes);
        while (count != -1) {
          jarOutputStream.write(bytes, 0, count);
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
