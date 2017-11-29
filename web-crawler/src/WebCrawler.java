import info.kgeorgiy.java.advanced.crawler.Crawler;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import info.kgeorgiy.java.advanced.crawler.Result;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebCrawler implements Crawler {

  private static final Logger logger = LoggerFactory.getLogger(WebCrawler.class);

  private static final Consumer<? super Node> NOTIFY =
      node -> {
        synchronized (node) {
          node.notify();
        }
      };

  private final ExecutorService downloadExecutors;
  private final ExecutorService extractExecutors;

  private final DownloadHandler downloadHandler;
  private final ExtractHandler extractHandler;

  public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
    logger.debug("Create WC with {} download threads and {} extract threads {}", downloaders, extractors);
    downloadExecutors = Executors.newFixedThreadPool(downloaders, new NamingThreadFactory("DownloaderThread%s"));
    extractExecutors = Executors.newFixedThreadPool(extractors, new NamingThreadFactory("ExtractorThread%s"));

    downloadHandler = new DownloadHandler(downloader);
    extractHandler = new ExtractHandler();
  }

  @Override
  public Result download(String url, int depth) {
    Node rootNode = new Node(url, 1, depth);
    asyncDownloadAndExtract(rootNode);
    try {
      return gatherDownloadedUrls(rootNode);
    } catch (InterruptedException e) {
      System.out.println("Interrupted...");
      return new Result(Collections.emptyList(), Collections.emptyMap());
    }
  }

  private void asyncDownloadAndExtract(Node node) {
    downloadExecutors.submit(
        () -> downloadHandler.download(node, node.isLeafNode() ? NOTIFY : this::asyncExtract)
    );

  }

  private void asyncExtract(Node downloadedNode) {
    extractExecutors.submit(
        () -> {
          extractHandler.extract(downloadedNode, this::asyncDownloadAndExtract);
          synchronized (downloadedNode) {
            downloadedNode.notify();
          }
        }
    );
  }

  private Result gatherDownloadedUrls(Node rootNode) throws InterruptedException {
    ArrayList<String> downloadedUrls = new ArrayList<>();
    HashMap<String, IOException> errors = new HashMap<>();

    Queue<Node> queue = new LinkedList<>();
    queue.add(rootNode);
    while (!queue.isEmpty()) {
      Node node = queue.poll();
      if (node.isLeafNode()) {
        downloadedUrls.add(node.getUrl());
      } else {
        synchronized (node) {
          while (node.getChilds().isEmpty() || node.getError() == null) {
            node.wait();
          }
        }
        if (node.getError() != null) {
          errors.put(node.getUrl(), node.getError());
        } else {
          queue.addAll(node.getChilds());
          downloadedUrls.add(node.getUrl());
        }
      }
    }

    return new Result(downloadedUrls, errors);
  }

  @Override
  public void close() {

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
