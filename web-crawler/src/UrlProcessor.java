import static java.util.stream.Collectors.toList;

import info.kgeorgiy.java.advanced.crawler.Document;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import info.kgeorgiy.java.advanced.crawler.Result;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UrlProcessor {

  private static final Logger log = LoggerFactory.getLogger(UrlProcessor.class);

  private final ExecutorService extractors;
  private final ExecutorService downloaders;

  private final DownloadHandler downloaderHandler;

  private final Set<String> processedUrls = new HashSet<>();
  private ReadWriteLock rwLock = new ReentrantReadWriteLock();

  public UrlProcessor(
      ExecutorService extractors,
      ExecutorService downloaders,
      DownloadHandler downloadHandler) {

    this.extractors = extractors;
    this.downloaders = downloaders;
    this.downloaderHandler = downloadHandler;
  }

  public Result processUrl(String url, int maxDepth) throws InterruptedException {
    if (maxDepth < 1) {
      throw new IllegalArgumentException("Max Depth should be greater than 1");
    }

    Node rootNode = new Node(url, 1, maxDepth);
    processNode(rootNode);
    return gatherDownloadedUrls(rootNode);
  }

  private void processNode(Node node) {
    boolean processedAlready;

    rwLock.readLock().lock();
    processedAlready = processedUrls.contains(node.getUrl());
    rwLock.readLock().unlock();

    if (!processedAlready) {
      rwLock.writeLock().lock();
      processedAlready = processedUrls.contains(node.getUrl());
      if (!processedAlready) {
        processedUrls.add(node.getUrl());
      }
      rwLock.writeLock().unlock();
    }

    if (!processedAlready) {
      if (isLeafNode(node)) {
        asyncDownload(node);
      } else {
        asyncDownloadAndExtract(node);
      }
    }

    if (processedAlready) {
      node.processed(true);
    }
  }

  private boolean isLeafNode(Node node) {
    return node.getNodeDepth() == node.getMaxDepth();
  }

  private void asyncDownload(Node node) {
    downloaders.submit(() -> {
      if (!Thread.currentThread().isInterrupted()) {
        try {
          Document document = downloaderHandler.download(node.getUrl());
          node.setDocument(document);

          node.processed();
        } catch (IOException e) {
          node.setError(e);
        } catch (InterruptedException e) {
          log.debug("Interrupted during download url {}", node.getUrl());
        }
      }
    });
  }

  private void asyncDownloadAndExtract(Node node) {
    downloaders.submit(() -> {
      if (!Thread.currentThread().isInterrupted()) {
        try {
          Document document = downloaderHandler.download(node.getUrl());
          node.setDocument(document);

          asyncExtractAndSendToProcessing(node);
        } catch (IOException e) {
          node.setError(e);
        } catch (InterruptedException e) {
          log.debug("Interrupted during download url {}", node.getUrl());
        }
      }
    });
  }

  private void asyncExtractAndSendToProcessing(Node node) {
    extractors.submit(() -> {
      if (!Thread.currentThread().isInterrupted()) {
        try {
          List<String> childLinks = node.getDocument().extractLinks();
          List<Node> childNodes = childLinks.stream()
              .map(cl -> new Node(cl, node.getNodeDepth() + 1, node.getMaxDepth()))
              .collect(toList());

          node.setChilds(childNodes);

          childNodes.forEach(this::processNode);

          node.processed();
        } catch (IOException e) {
          node.setError(e);
        }
      }
    });
  }

  // Blocking BFS
  private Result gatherDownloadedUrls(Node rootNode) throws InterruptedException {
    log.debug("Start gathering...");
    List<String> downloadedUrls = new ArrayList<>();
    HashMap<String, IOException> errors = new HashMap<>();

    Queue<Node> queue = new LinkedList<>();
    queue.add(rootNode);
    while (!queue.isEmpty()) {
      Node node = queue.poll();
      log.debug("The next node from queue: {}", node);

      node.waitProcessing();

      if (node.getError() != null) {
        log.debug("Node {} with error {}", node, node.getError());
        errors.put(node.getUrl(), node.getError());
      } else {
        if (!node.isRepeated()) {
          downloadedUrls.add(node.getUrl());
          if (!node.isLeafNode()) {
            log.debug("Add to queue child nodes: {}", node.getChilds());
            queue.addAll(node.getChilds());
          }
        }
      }
    }

    log.debug("Result downloaded size {}, error size {}", downloadedUrls.size(), errors.size());
    return new Result(new ArrayList<>(downloadedUrls), errors);
  }
}
