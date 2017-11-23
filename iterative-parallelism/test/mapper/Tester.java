package mapper;

import info.kgeorgiy.java.advanced.base.BaseTester;
import info.kgeorgiy.java.advanced.mapper.ListMapperTest;
import info.kgeorgiy.java.advanced.mapper.ScalarMapperTest;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/**
 * @author Georgiy Korneev (kgeorgiy@kgeorgiy.info)
 */
public class Tester extends BaseTester {
    public static void main(final String... args) throws NoSuchAlgorithmException, IOException {
        new Tester()
                .add("scalar", ScalarMapperTest.class)
                .add("list", ListMapperTest.class)
                .run(args);
    }
}
