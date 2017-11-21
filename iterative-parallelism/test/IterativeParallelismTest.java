import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.Test;

public class IterativeParallelismTest {

  private Random random = new Random(Instant.now().getEpochSecond());
  private List<Integer> ints =
      IntStream.range(0, random.nextInt(101))
          .map(i -> random.nextInt(1000))
          .boxed()
          .collect(toList());

  @Test
  public void testMinimum() throws InterruptedException {
    Integer expected = ints.stream()
        .min(Integer::compareTo)
        .orElseThrow(() -> new RuntimeException(":|"));


    Integer actual = new IterativeParallelism().minimum(8, ints, Integer::compareTo);

    assertEquals(expected, actual);
  }

  @Test
  public void testFilter() throws InterruptedException {
    List<Integer> expected = ints.stream()
        .filter(i -> i > 500)
        .collect(toList());

    List<Integer> actual = new IterativeParallelism().filter(8, ints, i -> i > 500);

    assertEquals(expected, actual);
  }

  @Test
  public void testJoin() throws InterruptedException {
    String expected = ints.stream().map(Object::toString).collect(joining());

    String actual = new IterativeParallelism().join(8, ints);

    assertEquals(expected, actual);
  }

  @Test
  public void testIfSizeLessThenThreads() throws InterruptedException {
    List<Integer> ints = this.ints.subList(0, 5);

    Integer expected = ints.stream().max(Comparator.reverseOrder())
        .orElseThrow(() -> new RuntimeException("OU"));

    Integer actual = new IterativeParallelism().maximum(8, ints, Comparator.reverseOrder());

    assertEquals(expected, actual);
  }
}