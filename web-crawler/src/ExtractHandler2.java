import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractHandler2 {

  private static final Logger logger = LoggerFactory.getLogger(ExtractHandler2.class);

  private final Set<String> extractedUrls = new HashSet<>();
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  public void extract(Node node, Consumer<? super Node> callBack) {
    logger.debug("Url '{}' starting extract processing", node.getUrl());
    boolean extractedAlready;

    rwLock.readLock().lock();
    extractedAlready = extractedUrls.contains(node.getUrl());
    rwLock.readLock().unlock();

    if (!extractedAlready) {
      rwLock.writeLock().lock();
      extractedAlready = extractedUrls.contains(node.getUrl());
      if (!extractedAlready) {
        logger.debug("Url '{}' add to already extracted", node.getUrl());
        extractedUrls.add(node.getUrl());
      }
      rwLock.writeLock().unlock();
    }

    if (!extractedAlready) {
      try {
        logger.debug("Url '{}' extracting", node.getUrl());
        List<String> childLinks = node.getDocument().extractLinks();
        logger.debug("Url '{}' extracted childLinks: {}", node.getUrl(), childLinks);
        List<Node> childNodes = childLinks.stream()
            .map(l -> new Node(l, node.getNodeDepth() + 1, node.getMaxDepth()))
            .collect(Collectors.toList());
        node.setChilds(childNodes);

        childNodes.forEach(cn -> {
          logger.debug("Invoke callback for CN {}", cn.getUrl());
          callBack.accept(cn);
        });
      } catch (IOException e) {
        node.setError(e);
      }
    }

    node.processed(extractedAlready);
  }
}
