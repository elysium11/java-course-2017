import static java.util.function.Predicate.isEqual;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class EnhancedIterativeParallelism extends IterativeParallelism {

  private ParallelMapper parallelMapper;

  public EnhancedIterativeParallelism() {
  }

  public EnhancedIterativeParallelism(ParallelMapper parallelMapper) {
    this.parallelMapper = parallelMapper;
  }

  @Override
  public <T> T minimum(int i, List<? extends T> list, Comparator<? super T> comparator)
      throws InterruptedException {

    if (parallelMapper != null) {
      List<List<? extends T>> chunks = splitToChunks(list, i);

      List<? extends T> minimums = parallelMapper.map(minimumFinder(comparator), chunks);

      return minimums.stream()
          .min(comparator)
          .orElseThrow(() -> new RuntimeException("Could not find min"));
    }

    return super.minimum(i, list, comparator);
  }

  @Override
  public <T> T maximum(int i, List<? extends T> list, Comparator<? super T> comparator)
      throws InterruptedException {

    if (parallelMapper != null) {
      List<List<? extends T>> chunks = splitToChunks(list, i);

      List<? extends T> maximums = parallelMapper.map(maximumFinder(comparator), chunks);

      return maximums.stream()
          .max(comparator)
          .orElseThrow(() -> new RuntimeException("Could not find max"));
    }

    return super.maximum(i, list, comparator);
  }

  @Override
  public <T> boolean all(int i, List<? extends T> list, Predicate<? super T> predicate)
      throws InterruptedException {

    if (parallelMapper != null) {
      List<List<? extends T>> chunks = splitToChunks(list, i);

      List<Boolean> matchResults = parallelMapper.map(allMatchChecker(predicate), chunks);

      return matchResults.stream().allMatch(isEqual(true));
    }

    return super.all(i, list, predicate);
  }

  @Override
  public <T> boolean any(int i, List<? extends T> list, Predicate<? super T> predicate)
      throws InterruptedException {

    if (parallelMapper != null) {
      List<List<? extends T>> chunks = splitToChunks(list, i);

      List<Boolean> matchResults = parallelMapper.map(anyMatchChecker(predicate), chunks);

      return matchResults.stream().anyMatch(isEqual(true));
    }

    return super.any(i, list, predicate);
  }

  @Override
  public String join(int i, List<?> list) throws InterruptedException {
    if (parallelMapper != null) {
      List<List<?>> chunks = splitToChunks(list, i);

      List<String> joinResults = parallelMapper.map(listJoiner(), chunks);

      return joinResults.stream()
          .collect(joining());
    }

    return super.join(i, list);
  }

  @Override
  public <T> List<T> filter(int i, List<? extends T> list, Predicate<? super T> predicate)
      throws InterruptedException {

    if (parallelMapper != null) {
      List<List<? extends T>> chunks = splitToChunks(list, i);

      List<List<T>> filterResult = parallelMapper.map(filter(predicate), chunks);

      return filterResult.stream()
          .flatMap(Collection::stream)
          .collect(toList());
    }

    return super.filter(i, list, predicate);
  }

  @Override
  public <T, U> List<U> map(int i, List<? extends T> list,
      Function<? super T, ? extends U> function) throws InterruptedException {

    if (parallelMapper != null) {
      List<List<? extends T>> chunks = splitToChunks(list, i);

      List<? extends List<? extends U>> mappingResult =
          parallelMapper.map(mapper(function), chunks);

      return mappingResult.stream()
          .flatMap(Collection::stream)
          .collect(toList());
    }

    return super.map(i, list, function);
  }
}
