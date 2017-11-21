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

    ListTask<T, T> findMinimumTask =
        l -> l.stream()
            .min(comparator)
            .orElseThrow(() -> new RuntimeException("Could not find min in array :("));

    chunks.forEach(c -> executors.add(startAndReturnThread(new Executor<>(c, findMinimumTask))));

    T min = null;
    for (Executor<T, T> executor : executors) {
      min = isBiggerOrNull(min, executor.getResult(), comparator) ? executor.getResult() : min;
    }

    return min;
  }

  private <T> List<List<? extends T>> splitToChunks(List<? extends T> list, int chunksNum) {
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

    ListTask<T, T> findMaximumTask =
        l -> l.stream()
            .max(comparator)
            .orElseThrow(() -> new RuntimeException("Could not find maximum :("));

    chunks.forEach(c -> executors.add(new Executor<>(c, findMaximumTask)));
    executors.forEach(Thread::start);

    T max = null;
    for (Executor<T, T> executor : executors) {
      max = isLessOrNull(max, executor.getResult(), comparator) ? executor.getResult() : max;
    }

    return max;
  }

  private <T> boolean isLessOrNull(T value, T comparing, Comparator<? super T> comparator) {
    return value == null || comparator.compare(value, comparing) < 0;
  }

  @Override
  public <T> boolean all(int i, List<? extends T> list, Predicate<? super T> predicate)
      throws InterruptedException {
    List<Executor<T, Boolean>> executors = new ArrayList<>();

    List<List<? extends T>> chunks = splitToChunks(list, i);

    ListTask<T, Boolean> allMatchTask =
        l -> {
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

    chunks.forEach(c -> executors.add(startAndReturnThread(new Executor<>(c, allMatchTask))));

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

  @Override
  public <T> boolean any(int i, List<? extends T> list, Predicate<? super T> predicate)
      throws InterruptedException {
    List<Executor<T, Boolean>> executors = new ArrayList<>();

    List<List<? extends T>> chunks = splitToChunks(list, i);

    ListTask<T, Boolean> anyMatchTask =
        l -> {
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

    chunks.forEach(c -> executors.add(startAndReturnThread(new Executor<>(c, anyMatchTask))));

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

  @Override
  public String join(int i, List<?> list) throws InterruptedException {
    List<Executor<?, String>> executors = new ArrayList<>();

    List<List<?>> chunks = splitToChunks(list, i);

    ListTask<Object, String> joinTask = l -> l.stream()
        .map(Object::toString)
        .collect(joining());

    chunks.forEach(chunk -> executors.add(startAndReturnThread(new Executor<>(chunk, joinTask))));

    StringBuilder stringBuilder = new StringBuilder();
    for (Executor<?, String> executor : executors) {
      stringBuilder.append(executor.getResult());
    }

    return stringBuilder.toString();
  }

  @Override
  public <T> List<T> filter(int i, List<? extends T> list, Predicate<? super T> predicate)
      throws InterruptedException {

    List<Executor<T, List<T>>> executors = new ArrayList<>();

    List<List<? extends T>> chunks = splitToChunks(list, i);

    ListTask<T, List<T>> filterTask =
        l -> l.stream()
            .filter(predicate)
            .collect(toList());

    chunks.forEach(c -> executors.add(startAndReturnThread(new Executor<>(c, filterTask))));

    List<T> result = new ArrayList<>();
    for (Executor<T, List<T>> executor : executors) {
      result.addAll(executor.getResult());
    }

    return result;
  }

  @Override
  public <T, U> List<U> map(int i, List<? extends T> list,
      Function<? super T, ? extends U> function) throws InterruptedException {
    List<Executor<T, List<U>>> executors = new ArrayList<>();

    List<List<? extends T>> chunks = splitToChunks(list, i);

    ListTask<T, List<U>> mapTask =
        l -> l.stream()
            .map(function)
            .collect(toList());

    chunks.forEach(c -> executors.add(startAndReturnThread(new Executor<>(c, mapTask))));

    List<U> result = new ArrayList<>();
    for (Executor<T, List<U>> executor : executors) {
      result.addAll(executor.getResult());
    }

    return result;
  }

  private static class Executor<T, R> extends Thread {

    private final List<? extends T> array;
    private final ListTask<T, R> task;
    private R result;

    public Executor(List<? extends T> array, ListTask<T, R> task) {
      this.array = array;
      this.task = task;
    }

    public R getResult() throws InterruptedException {
      join();
      return result;
    }

    @Override
    public void run() {
      result = task.call(array);
    }
  }

  private interface ListTask<T, R> {

    R call(List<? extends T> list);
  }
}
