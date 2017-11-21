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
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.getLastModifiedTime;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.write;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

/**
 * Provides implementation to {@link JarImpler} interface.
 *
 * @see info.kgeorgiy.java.advanced.implementor.JarImpler
 */
public class Implementor implements JarImpler {

  /**
   * Comparator which allows to compare methods by their signature.
   */
  private static final Comparator<? super Method> METHOD_SIGNATURE_COMPARATOR =
      Comparator.comparing(m -> m.getName() + Arrays.toString(m.getParameterTypes()));

  /**
   * String representation of default values for primitive types;
   */
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

  /**
   * Path to implemented java file.
   */
  private Path implementationPath;
  /**
   * Path to implemented jar file.
   */
  private Path jarImplementationPath;

  /**
   * Returns implemented jar file path.
   *
   * @return path to jar with implementation
   */
  public Path getJarImplementationPath() {
    return jarImplementationPath;
  }

  /**
   * Implements passed class or interface and writes this implementation as java source file to
   * specified directory with preserving package structure of implemented class or interface. Result
   * class includes implementation for all not implemented (abstract) methods, both declared in
   * implementing class and inherited from ancestors, and for all available constructors declared in
   * implementing class.
   *
   * <p>Body of implemented methods consists of returning default value of method's return type.
   * Each constructor implementation call appropriate one from implementing class.</p>
   *
   * <p>Name of implementation class is composed of implementing class name and suffix 'Impl'.</p>
   *
   * @param aClass implementing class
   * @param path directory in which implementation should be written
   * @throws ImplerException if passed class could not be implemented
   * @throws UncheckedIOException if IO exception occurs while writing to result file.
   */
  @Override
  public void implement(Class<?> aClass, Path path) throws ImplerException {
    String implementationName = aClass.getSimpleName() + "Impl";

    String implementation = generateImplementation(aClass, implementationName);

    try {
      Path packagePath = Paths.get(aClass.getPackage().getName().replace(".", "/"));
      implementationPath = path.resolve(packagePath).resolve(implementationName + ".java");

      createDirectories(implementationPath.getParent());

      write(
          implementationPath.toAbsolutePath(),
          implementation.getBytes(UTF_8),
          CREATE,
          TRUNCATE_EXISTING
      );
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Generates implementation code for specified class.
   *
   * @param parent implementing class
   * @param implementationName name of implementation class
   * @return generated implementation code including package and class declaration as well as
   * constructors and not implemented methods
   * @throws ImplerException if implementing class could not be inherited: has only private
   * constructors, contains final modifier in class declaration and so on.
   */
  private String generateImplementation(Class<?> parent, String implementationName)
      throws ImplerException {
    if (parent.isPrimitive() || parent.isEnum() || parent.equals(Enum.class) || parent.isArray() ||
        isFinal(parent.getModifiers()) || areAllConstructorsPrivate(parent)) {
      throw new ImplerException(
          "Implementing class should not be primitive, array, enum or final.");
    }

    String packageDeclaration = composePackageDeclaration(parent);

    String classDeclaration = composeClassDeclaration(parent, implementationName);

    String constructorsImplementation = getConstructorsImplementation(
        parent.getDeclaredConstructors(),
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

  /**
   * Checks all constructors of passed class for private modifier and if all match return true.
   *
   * @param aClass class from which constructors would be obtained
   * @return boolean value which defines conformity of all constructors of passed class to private
   * access modifier
   */
  private boolean areAllConstructorsPrivate(Class<?> aClass) {
    return aClass.getDeclaredConstructors().length > 0 &&
        stream(aClass.getDeclaredConstructors())
            .allMatch(c -> isPrivate(c.getModifiers()));
  }

  /**
   * Composes package declaration.
   *
   * @param parent implementing class
   * @return package declaration as string
   */
  private String composePackageDeclaration(Class<?> parent) {
    return format("package %s;", parent.getPackage().getName());
  }

  /**
   * Composes implementation class declaration.
   *
   * @param parent implementing class
   * @param className implementation class name
   * @return class declaration as string
   */
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

  /**
   * Returns implementation code for all not private constructors.
   *
   * @param constructors constructors need to be implemented
   * @param className implementation class name
   * @return constructors implementation code as string
   */
  private String getConstructorsImplementation(Constructor<?>[] constructors, String className) {
    return stream(constructors)
        .filter(c -> !isPrivate(c.getModifiers()))
        .map(c -> composeConstructorImplementation(c, className))
        .collect(joining(lineSeparator() + lineSeparator()));

  }

  /**
   * Composes implementation code for appropriate constructor.
   *
   * @param constructor appropriate constructor from implementing class
   * @param className implementation class name
   * @return composed constructor implementation code
   */
  private String composeConstructorImplementation(Constructor<?> constructor, String className) {
    Parameter[] parameters = constructor.getParameters();

    String accessModifier = getAccessModifier(constructor);

    String parametersDeclaration = getParametersDeclaration(parameters);

    String throwsDeclaration = getThrowsDeclaration(constructor.getExceptionTypes());

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

  /**
   * Returns part of method or constructor declaration in which the parameters are described.
   *
   * @param parameters list of method or constructor parameters
   * @return parameters part of method or constructor declaration
   */
  private String getParametersDeclaration(Parameter[] parameters) {
    return stream(parameters)
        .map(p -> getParameterType(p) + " " + p.getName())
        .collect(joining(", ", "(", ")"));
  }

  /**
   * Returns part of method or constructor declaration in which the exceptions throwable by method
   * are described.
   *
   * @param exceptionTypes list of method or constructor exceptions
   * @return 'throws' part of method or constructor declaration. if exceptionTypes is empty returns
   * empty string.
   */
  private String getThrowsDeclaration(Class<?>[] exceptionTypes) {
    if (exceptionTypes.length == 0) {
      return "";
    }

    return stream(exceptionTypes)
        .map(Class::getCanonicalName)
        .collect(joining(", ", "throws ", ""));
  }

  /**
   * Returns string representation of parameter type. Main goal of this method is proper handling
   * varargs parameter case.
   *
   * @param parameter handling parameter
   * @return string representation of parameter type
   */
  private String getParameterType(Parameter parameter) {
    if (parameter.isVarArgs()) {
      return parameter.getType().getComponentType().getCanonicalName() + "...";
    } else {
      return handleType(parameter.getType());
    }
  }

  /**
   * Returns string representation of type. Main goal of this method is proper handling array type
   * case.
   *
   * @param type handling type
   * @return string representation of handling type
   */
  private String handleType(Class<?> type) {
    if (type.isArray()) {
      return type.getComponentType().getCanonicalName() + "[]";
    } else {
      return type.getCanonicalName();
    }
  }

  /**
   * Returns implementation code for not implemented methods of parent or his ancestors.
   *
   * @param parent parent class or interface
   * @return method implementation code as string
   */
  private String getMethodsImplementation(Class<?> parent) {
    Collection<Method> notImplementedMethods = extractNotImplementedMethods(parent);

    return notImplementedMethods.stream()
        .map(this::composeMethodImplementation)
        .collect(joining(lineSeparator()));
  }

  /**
   * Returns collection which comprises not implemented methods from parent class hierarchy.
   *
   * @param parent parent class
   * @return collection of not implemented methods
   */
  private Collection<Method> extractNotImplementedMethods(Class<?> parent) {
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

  /**
   * Checks is method could be overridden in child class.
   *
   * @param method checking method
   * @return boolean representation of checking
   */
  private boolean isImplementable(Method method) {
    int modifiers = method.getModifiers();
    return !isPrivate(modifiers) && !isStatic(modifiers) && !isNative(modifiers);
  }

  /**
   * Composes implementation code for method.
   *
   * @param method implementing method
   * @return implementation code as string
   */
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

  /**
   * Return access modifiers for method or constructor.
   *
   * @param executable method or constructor
   * @return string representation of access modifier
   */
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

  /**
   * Implements specified class or interface using {@link Implementor#implement(Class, Path)},
   * compiles it, archive compilation result to jar file and save in specified directory.
   * Implementation java file would be created in same directory with result jar file path and would
   * be deleted after compilation. Result jar file name consist of implementation class name with
   * 'Impl' suffix.
   *
   * @param aClass implementing class
   * @param path directory in which result jar file would be saved.
   * @throws ImplerException if specified class could not be implemented
   * @throws UncheckedIOException if IO exception occurs while deleting implemented java file or
   */
  @Override
  public void implementJar(Class<?> aClass, Path path) throws ImplerException {
    Path rootPackagePath = path.toAbsolutePath().resolve(getRootPackage(aClass));

    implement(aClass, path);
    compileJavaFile(implementationPath);
    jarImplementationPath = path.resolve(aClass.getSimpleName() + "Impl.jar");

    try {
      Files.deleteIfExists(implementationPath);
      archiveToJar(rootPackagePath, jarImplementationPath);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Returns root package name of passed class.
   *
   * @param aClass class from which root package would be retrieved
   * @return root package name as string
   */
  private String getRootPackage(Class<?> aClass) {
    String packageName = aClass.getPackage().getName();

    if (packageName.length() != 0) {
      return packageName.split("\\.")[0];
    }

    return "";
  }

  /**
   * Compiles java source file obtained by passed path with default system compiler using
   * {@link JavaCompiler#run(InputStream, OutputStream, OutputStream, String...)}.
   *
   * @param file path to java source file
   * @see JavaCompiler
   */
  private void compileJavaFile(Path file) {
    JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
    javaCompiler.run(null, null, null, file.toString());
  }

  /**
   * Archive to jar passed package including all underlying files using {@link
   * Implementor#writeToJar(JarOutputStream, Path)}.
   *
   * @param archivingPackagePath archiving package
   * @param jarPath path to result jar file
   * @throws IOException if any IO errors occurs while writing to jar
   * @see JarOutputStream
   */
  private void archiveToJar(Path archivingPackagePath, Path jarPath) throws IOException {
    try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
      walk(archivingPackagePath)
          .forEach(p -> writeToJar(jarOutputStream, p));
    }
  }

  /**
   * Writes passed file to jar archive using specified {@link JarOutputStream}.
   *
   * @param jarOutputStream jar output stream
   * @param file archiving file
   * @see JarEntry
   * @see JarOutputStream
   */
  private void writeToJar(JarOutputStream jarOutputStream, Path file) {
    String fileName = file.getFileName().toString();
    if (isDirectory(file)) {
      fileName += "/";
    }
    JarEntry jarEntry = new JarEntry(fileName);
    if (isDirectory(file)) {
      try {
        jarEntry.setLastModifiedTime(getLastModifiedTime(file));
        jarOutputStream.putNextEntry(jarEntry);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      try (InputStream in = newInputStream(file)) {
        jarEntry.setLastModifiedTime(getLastModifiedTime(file));
        jarOutputStream.putNextEntry(jarEntry);
        byte[] bytes = new byte[4096];
        int count = in.read(bytes);
        while (count != -1) {
          jarOutputStream.write(bytes, 0, count);
          count = in.read(bytes);
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
