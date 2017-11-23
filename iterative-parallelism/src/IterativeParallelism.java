import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import info.kgeorgiy.java.advanced.concurrent.ListIP;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class IterativeParallelism implements ListIP {

  @Override
  public <T> T minimum(int i, List<? extends T> list, Comparator<? super T> comparator)
      throws InterruptedException {
    List<Executor<T, T>> executors = new ArrayList<>(i);

    List<List<? extends T>> chunks = splitToChunks(list, i);

    chunks.forEach(
        c -> executors.add(startAndReturnThread(new Executor<>(c, minimumFinder(comparator)))));

    T min = null;
    for (Executor<T, T> executor : executors) {
      min = isBiggerOrNull(min, executor.getResult(), comparator) ? executor.getResult() : min;
    }

    return min;
  }

  <T> ListFunction<T, T> minimumFinder(Comparator<? super T> comparator) {
    return l -> l.stream()
        .min(comparator)
        .orElseThrow(() -> new RuntimeException("Could not find min in array :("));
  }

  <T> List<List<? extends T>> splitToChunks(List<? extends T> list, int chunksNum) {
    List<List<? extends T>> chunks = new ArrayList<>();
    int chunkSize = list.size() / chunksNum;

    if (chunkSize <= 0) {
      chunksNum = 1;
      chunkSize = list.size();
    }

    for (int i = 0; i < chunksNum; i++) {
      int fromIndex = i * chunkSize;
      int toIndex = chunkSize * (i + 1);
      chunks.add(list.subList(fromIndex, toIndex));
    }

    if (list.size() % chunksNum > 0) {
      chunks.add(list.subList(chunksNum * chunkSize, list.size()));
    }

    return chunks;
  }

  private <T extends Thread> T startAndReturnThread(T thread) {
    thread.start();
    return thread;
  }

  private <T> boolean isBiggerOrNull(T value, T comparing, Comparator<? super T> comparator) {
    return value == null || comparator.compare(value, comparing) > 0;
  }


  @Override
  public <T> T maximum(int i, List<? extends T> list, Comparator<? super T> comparator)
      throws InterruptedException {
    List<Executor<T, T>> executors = new ArrayList<>();

    List<List<? extends T>> chunks = splitToChunks(list, i);

    chunks.forEach(
        c -> executors.add(startAndReturnThread(new Executor<>(c, maximumFinder(comparator)))));

    T max = null;
    for (Executor<T, T> executor : executors) {
      max = isLessOrNull(max, executor.getResult(), comparator) ? executor.getResult() : max;
    }

    return max;
  }

  <T> ListFunction<T, T> maximumFinder(Comparator<? super T> comparator) {
    return l -> l.stream()
        .max(comparator)
        .orElseThrow(() -> new RuntimeException("Could not find maximum :("));
  }

  private <T> boolean isLessOrNull(T value, T comparing, Comparator<? super T> comparator) {
    return value == null || comparator.compare(value, comparing) < 0;
  }

  @Override
  public <T> boolean all(int i, List<? extends T> list, Predicate<? super T> predicate)
      throws InterruptedException {
    List<Executor<T, Boolean>> executors = new ArrayList<>();

    List<List<? extends T>> chunks = splitToChunks(list, i);

    chunks.forEach(
        c -> executors.add(startAndReturnThread(new Executor<>(c, allMatchChecker(predicate)))));

    for (int j = 0; j < executors.size(); j++) {
      Executor<T, Boolean> executor = executors.get(j);

      Boolean allMatchedInChunk = executor.getResult();
      if (!allMatchedInChunk) {
        executors.stream().skip(j).forEach(Thread::interrupt);
        return false;
      }
    }

    return true;
  }

  <T> ListFunction<T, Boolean> allMatchChecker(Predicate<? super T> predicate) {
    return l -> {
      for (T element : l) {
        if (Thread.currentThread().isInterrupted()) {
          return null;
        }
        if (!predicate.test(element)) {
          return false;
        }
      }
      return true;
    };
  }

  @Override
  public <T> boolean any(int i, List<? extends T> list, Predicate<? super T> predicate)
      throws InterruptedException {
    List<Executor<T, Boolean>> executors = new ArrayList<>();

    List<List<? extends T>> chunks = splitToChunks(list, i);

    chunks.forEach(
        c -> executors.add(startAndReturnThread(new Executor<>(c, anyMatchChecker(predicate)))));

    for (int j = 0; j < executors.size(); j++) {
      Executor<T, Boolean> executor = executors.get(j);

      Boolean anyMatchedInChunk = executor.getResult();
      if (anyMatchedInChunk) {
        executors.stream().skip(j).forEach(Thread::interrupt);
        return true;
      }
    }

    return false;
  }

  <T> ListFunction<T, Boolean> anyMatchChecker(Predicate<? super T> predicate) {
    return l -> {
      for (T element : l) {
        if (Thread.currentThread().isInterrupted()) {
          return null;
        }
        if (predicate.test(element)) {
          return true;
        }
      }
      return false;
    };
  }

  @Override
  public String join(int i, List<?> list) throws InterruptedException {
    List<Executor<?, String>> executors = new ArrayList<>();

    List<List<?>> chunks = splitToChunks(list, i);

    chunks
        .forEach(chunk -> executors.add(startAndReturnThread(new Executor<>(chunk, listJoiner()))));

    StringBuilder stringBuilder = new StringBuilder();
    for (Executor<?, String> executor : executors) {
      stringBuilder.append(executor.getResult());
    }

    return stringBuilder.toString();
  }

  ListFunction<Object, String> listJoiner() {
    return l -> l.stream()
        .map(Object::toString)
        .collect(joining());
  }

  @Override
  public <T> List<T> filter(int i, List<? extends T> list, Predicate<? super T> predicate)
      throws InterruptedException {

    List<Executor<T, List<T>>> executors = new ArrayList<>();

    List<List<? extends T>> chunks = splitToChunks(list, i);

    chunks.forEach(c -> executors.add(startAndReturnThread(new Executor<>(c, filter(predicate)))));

    List<T> result = new ArrayList<>();
    for (Executor<T, List<T>> executor : executors) {
      result.addAll(executor.getResult());
    }

    return result;
  }

  <T> ListFunction<T, List<T>> filter(Predicate<? super T> predicate) {
    return l -> l.stream()
        .filter(predicate)
        .collect(toList());
  }

  @Override
  public <T, U> List<U> map(int i, List<? extends T> list,
      Function<? super T, ? extends U> function) throws InterruptedException {
    List<Executor<T, List<U>>> executors = new ArrayList<>();

    List<List<? extends T>> chunks = splitToChunks(list, i);

    chunks.forEach(c -> executors.add(startAndReturnThread(new Executor<>(c, mapper(function)))));

    List<U> result = new ArrayList<>();
    for (Executor<T, List<U>> executor : executors) {
      result.addAll(executor.getResult());
    }

    return result;
  }

  <T, U> ListFunction<T, List<U>> mapper(Function<? super T, ? extends U> function) {
    return l -> l.stream()
        .map(function)
        .collect(toList());
  }

  static class Executor<T, R> extends Thread {

    private final List<? extends T> array;
    private final ListFunction<T, R> listFunction;
    private R result;

    public Executor(List<? extends T> array, ListFunction<T, R> listFunction) {
      this.array = array;
      this.listFunction = listFunction;
    }

    public R getResult() throws InterruptedException {
      join();
      return result;
    }

    @Override
    public void run() {
      result = listFunction.apply(array);
    }
  }

  interface ListFunction<T, R> extends Function<List<? extends T>, R> {}
}
