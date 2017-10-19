package ru.ilnurkhafizoff.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.examples.ClassWithPackagePrivateConstructor;
import info.kgeorgiy.java.advanced.implementor.examples.InterfaceWithStaticMethod;
import java.nio.file.Paths;
import java.util.AbstractList;
import javax.imageio.plugins.bmp.BMPImageWriteParam;
import javax.management.Descriptor;
import org.junit.Test;

public class ImplementorTest {

  @Test
  public void abstractListImplementationTest() throws ImplerException {
    new Implementor().implement(AbstractList.class, Paths.get("."));
  }

  @Test
  public void BMPImageWriteParamImplementationTest() throws ImplerException {
    new Implementor().implement(BMPImageWriteParam.class, Paths.get("."));
  }

  @Test
  public void DescriptorImplementationTest() throws ImplerException {
    new Implementor().implement(Descriptor.class, Paths.get("."));
  }

  @Test
  public void withPackagePrivateConstructorImplementationTest() throws ImplerException {
    new Implementor().implement(ClassWithPackagePrivateConstructor.class, Paths.get("."));
  }

  @Test
  public void implementJarTest() throws ImplerException {
    new Implementor().implementJar(InterfaceWithStaticMethod.class, Paths.get("."));
  }
}