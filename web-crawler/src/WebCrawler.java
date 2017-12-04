import info.kgeorgiy.java.advanced.crawler.Crawler;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import info.kgeorgiy.java.advanced.crawler.Result;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebCrawler implements Crawler {

  private static final Logger log = LoggerFactory.getLogger(WebCrawler.class);

  private static final Consumer<? super Node> LEAF_NODE_CALLBACK = Node::processed;

  private final ExecutorService downloadExecutors;
  private final ExecutorService extractExecutors;

  private final DownloadHandler2 downloadHandler;
  private final ExtractHandler2 extractHandler;

  Downloader downloader;

  public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
    log.debug("Create WC with {} download threads and {} extract threads {}", downloaders,
        extractors);
    downloadExecutors = Executors
        .newFixedThreadPool(downloaders, new NamingThreadFactory("DownloaderThread-%s"));
    extractExecutors = Executors
        .newFixedThreadPool(extractors, new NamingThreadFactory("ExtractorThread-%s"));

    this.downloader = downloader;
    downloadHandler = new DownloadHandler2(downloader);
    extractHandler = new ExtractHandler2();
  }

  @Override
  public Result download(String url, int depth) {
    Node rootNode = new Node(url, 1, depth);
    DownloadHandler2 downloadHandler2 = new DownloadHandler2(downloader);
    ExtractHandler2 extractHandler2 = new ExtractHandler2();

    asyncDownloadAndExtract(rootNode, downloadHandler2, extractHandler2);
    try {
      return gatherDownloadedUrls(rootNode);
    } catch (InterruptedException e) {
      System.out.println("Interrupted...");
      return new Result(Collections.emptyList(), Collections.emptyMap());
    }
  }

  private void asyncDownloadAndExtract(Node node, DownloadHandler2 downloadHandler,
      ExtractHandler2 extractHandler) {
    log.debug("Node {} came to download and extract", node);
    downloadExecutors.submit(
        () -> downloadHandler.download(node,
            node.isLeafNode() ? LEAF_NODE_CALLBACK
                : (n) -> asyncExtract(node, downloadHandler, extractHandler))
    );
  }

  private void asyncExtract(Node node, DownloadHandler2 downloadHandler,
      ExtractHandler2 extractHandler) {
    extractExecutors.submit(
        () -> extractHandler
            .extract(node, (n) -> asyncDownloadAndExtract(n, downloadHandler, extractHandler))
    );
  }

//  private void asyncDownloadAndExtract(Node node, DownloadHandler2 downloadHandler,
//      ExtractHandler extractHandler) {
//    log.debug("Node {} came to download and extract", node);
//    downloadExecutors.submit(
//        () -> downloadHandler.download(node, node.isLeafNode() ? LEAF_NODE_CALLBACK : this::asyncExtract)
//    );
//
//  }

  // FIRST
//  private void asyncDownloadAndExtract(Node node) {
//    log.debug("Node {} came to download and extract", node);
//    downloadExecutors.submit(
//        () -> downloadHandler.download(node, node.isLeafNode() ? LEAF_NODE_CALLBACK : this::asyncExtract)
//    );
//
//  }

  // FIRST
//  private void asyncExtract(Node downloadedNode) {
//    extractExecutors.submit(
//        () -> extractHandler.extract(downloadedNode, this::asyncDownloadAndExtract)
//    );
//  }

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

  @Override
  public void close() {
    extractExecutors.shutdown();
    downloadExecutors.shutdown();
  }
}

class NamingThreadFactory implements ThreadFactory {

  private String nameFormat;
  private static int counter = 1;

  public NamingThreadFactory(String nameFormat) {
    this.nameFormat = nameFormat;
  }

  @Override
  public Thread newThread(Runnable r) {
    return new Thread(r, String.format(nameFormat, counter++));
  }
}
