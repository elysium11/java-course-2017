import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ExtractHandler {

  private static final List<String> ERROR_EXTRACTION = Collections.emptyList();

  private final Map<String, List<Node>> waitingForExtract = new HashMap<>();
  private final Map<String, List<String>> extractedUrls = new HashMap<>();
  private final ReadWriteLock extractedUrlsLock = new ReentrantReadWriteLock();

  public void extract(Node node, Consumer<Node> callBack) {
    List<String> extractionResult;
    List<Node> waiters = null;

    extractedUrlsLock.readLock().lock();
    extractionResult = extractedUrls.get(node.getUrl());
    extractedUrlsLock.readLock().unlock();

    if (extractionResult == null) {
      synchronized (waitingForExtract) {
        if (waitingForExtract.containsKey(node.getUrl())) {
          waitingForExtract.get(node.getUrl()).add(node);
          return;
        } else {
          waitingForExtract.put(node.getUrl(), initWaiters(node));
        }
      }

      try {
        extractionResult = node.getDocument().extractLinks();
      } catch (IOException e) {
        extractionResult = ERROR_EXTRACTION;
        node.setError(e);
      }

      synchronized (waitingForExtract) {
        waiters = waitingForExtract.remove(node.getUrl());
        extractedUrlsLock.writeLock().lock();
        extractedUrls.put(node.getUrl(), extractionResult);
        extractedUrlsLock.writeLock().unlock();
      }
    }

    List<Node> childNodes = extractionResult.stream()
        .map(url -> new Node(url, node.getNodeDepth() + 1, node.getMaxDepth()))
        .collect(Collectors.toList());

    waiters.forEach(w -> w.setChilds(childNodes));

    childNodes.forEach(callBack);
  }

  private ArrayList<Node> initWaiters(Node node) {
    return new ArrayList<Node>() {{
      add(node);
    }};
  }
}
