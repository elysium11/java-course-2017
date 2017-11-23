import info.kgeorgiy.java.advanced.mapper.ParallelMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ParallelMapperImpl implements ParallelMapper {

  private final ExecutorService executorService;

  public ParallelMapperImpl(int threads) {
    this.executorService = Executors.newFixedThreadPool(threads);
  }

  @Override
  public <T, R> List<R> map(Function<? super T, ? extends R> function, List<? extends T> args)
      throws InterruptedException {

    List<Future<? extends R>> futureResults = args.stream()
        .map(arg -> (Callable<? extends R>) createTask(function, arg))
        .map(executorService::submit)
        .collect(Collectors.toList());

    List<R> results = new ArrayList<>();
    for (Future<? extends R> future : futureResults) {
      try {
        results.add(future.get());
      } catch (ExecutionException e) { // there is no way to change throws clause in declaring interface
        Throwable cause = e.getCause();
        if (cause instanceof InterruptedException) {
          throw (InterruptedException) cause;
        }
        throw new RuntimeException(e);
      }
    }

    return results;
  }

  private <T, R> Callable<? extends R> createTask(Function<? super T, ? extends R> f, T a) {
    return () -> {
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException("Interrupted :(");
      }
      return f.apply(a);
    };
  }

  @Override
  public void close() throws InterruptedException {
    executorService.shutdown();
  }
}
