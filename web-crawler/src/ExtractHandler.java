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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractHandler {

  private static final Logger log = LoggerFactory.getLogger(ExtractHandler.class);

  private static final List<String> ERROR_EXTRACTION = Collections.emptyList();

  private final Map<String, List<Node>> waitingForExtract = new HashMap<>();
  private final Map<String, List<String>> extractedUrls = new HashMap<>();
  private final ReadWriteLock extractedUrlsLock = new ReentrantReadWriteLock();

  public void extract(Node node, Consumer<Node> callBack) {
    log.debug("[{}]: Starting extract url '{}'", Thread.currentThread().getName(), node.getUrl());
    List<String> extractionResult;
    List<Node> waiters = null;

    log.debug("Try to find url '{}' in already processed #1", node.getUrl());
    extractionResult = searchInAlreadyExtracted(node);
    if (extractionResult != null) {
      log.debug("Url '{}' found in already processed", node.getUrl());
      callBackForEachChildNode(node, callBack, extractionResult);
      return;
    }

    synchronized (waitingForExtract) {

      log.debug("Try to find url '{}' in already processed #2", node.getUrl());
      extractionResult = searchInAlreadyExtracted(node);
      if (extractionResult != null) {
        log.debug("Url '{}' found in already processed", node.getUrl());
        callBackForEachChildNode(node, callBack, extractionResult);
        return;
      }

      log.debug("Try to find url '{}' in waiters list", node.getUrl());
      if (waitingForExtract.containsKey(node.getUrl())) {
        log.debug("Url '{}' found in waiters list => add to waiters and return", node.getUrl());
        waitingForExtract.get(node.getUrl()).add(node);
        return;
      } else {
        log.debug("Url '{}' not found in waiters list => add waiter and go to download",
            node.getUrl());
        waitingForExtract.put(node.getUrl(), initWaiters(node));
      }
    }

    try {
      log.debug("Try to extract url '{}'", node.getUrl());
      extractionResult = node.getDocument().extractLinks();
      log.debug("Extraction Result: '{}'", extractionResult);
    } catch (IOException e) {
      log.debug("Error '{}' occurred during extracting url '{}' ", e, node.getUrl());
      extractionResult = ERROR_EXTRACTION;
      node.setError(e);
    }

    synchronized (waitingForExtract) {
      log.debug("Remove waiters for url '{}'", node.getUrl());
      waiters = waitingForExtract.remove(node.getUrl());
      log.debug("Waiters size for url '{}': {}", node.getUrl(), waiters.size());
      extractedUrlsLock.writeLock().lock();
      log.debug("Put url '{}' to processed", node.getUrl());
      extractedUrls.put(node.getUrl(), extractionResult);
      extractedUrlsLock.writeLock().unlock();
    }

    log.debug("Create child nodes for waiters {}", waiters);
    for (Node waiter : waiters) {
      callBackForEachChildNode(waiter, callBack, extractionResult);
//      List<Node> childNodes = extractionResult.stream()
//          .map(url -> new Node(url, waiter.getNodeDepth() + 1, waiter.getMaxDepth()))
//          .collect(Collectors.toList());
//      waiter.setChilds(childNodes);
//      log.debug("Invoke extract callback for url '{}' to nodes: {}", waiter.getUrl(), childNodes);
//      childNodes.forEach(callBack);
//      waiter.processed();
    }
  }

  void callBackForEachChildNode(Node node, Consumer<Node> callBack, List<String> extractionResult) {
    List<Node> childNodes = extractionResult.stream()
        .map(url -> new Node(url, node.getNodeDepth() + 1, node.getMaxDepth()))
        .collect(Collectors.toList());

    node.setChilds(childNodes);
    node.processed();

    log.debug("Invoke callback for url '{}'", node.getUrl());
    childNodes.forEach(callBack);
  }

  private List<String> searchInAlreadyExtracted(Node node) {
    extractedUrlsLock.readLock().lock();
    List<String> extractionResult = extractedUrls.get(node.getUrl());
    extractedUrlsLock.readLock().unlock();
    return extractionResult;
  }

  private ArrayList<Node> initWaiters(Node node) {
    return new ArrayList<Node>() {{
      add(node);
    }};
  }
}
