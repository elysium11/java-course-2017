import info.kgeorgiy.java.advanced.crawler.Document;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadHandler {

  private static Logger log = LoggerFactory.getLogger(DownloadHandler.class);

  private static final Document ERROR_DOCUMENT = Collections::emptyList;

  private final Map<String, List<Node>> waitingForDownload = new HashMap<>();
  private final Map<String, Document> downloadedUrls = new HashMap<>();
  private final ReadWriteLock downloadedUrlsLock = new ReentrantReadWriteLock();

  private final Downloader downloader;

  public DownloadHandler(Downloader downloader) {
    this.downloader = downloader;
  }

  public void download(Node node, Consumer<? super Node> callBack) {
    Document document;
    List<Node> waiters = null;

    log.debug("[{}]: Starting download url '{}'", Thread.currentThread().getName(), node.getUrl());
    downloadedUrlsLock.readLock().lock();
    document = downloadedUrls.get(node.getUrl());
    log.debug("[{}]: Searching in downloaded urls result '{}'", Thread.currentThread().getName(),
        document);
    downloadedUrlsLock.readLock().unlock();

    if (document == null) {
      synchronized (waitingForDownload) {

        log.debug("[{}]: Try found node '{}' in waiting list ", Thread.currentThread().getName(),
            node.getUrl());
        downloadedUrlsLock.readLock().lock();
        document = downloadedUrls.get(node.getUrl());
        log.debug("[{}]: Searching in downloaded urls result '{}'",
            Thread.currentThread().getName(),
            document);
        downloadedUrlsLock.readLock().unlock();

        if (document == null) {
          if (waitingForDownload.containsKey(node.getUrl())) {
            log.debug("[{}]: Node '{}' found => add to waiters", Thread.currentThread().getName(),
                node.getUrl());
            waitingForDownload.get(node.getUrl()).add(node);
            return;
          } else {
            log.debug("[{}]: Node '{}' not found => need to download",
                Thread.currentThread().getName(),
                node.getUrl());
            waitingForDownload.put(node.getUrl(), initWaiters(node));
          }
        }
      }

      if (document == null) {
        try {
          log.debug("[{}]: Try to download '{}'", Thread.currentThread().getName(), node.getUrl());
          document = downloader.download(node.getUrl());
        } catch (IOException e) {
          log.debug("[{}]: Error '{}' during download '{}'", Thread.currentThread().getName(), e,
              node.getUrl());
          node.setError(e);
          document = ERROR_DOCUMENT;
        }

        synchronized (waitingForDownload) {
          log.debug("[{}]: Remove '{}' from waiters", Thread.currentThread().getName(),
              node.getUrl());
          waiters = waitingForDownload.remove(node.getUrl());
          downloadedUrlsLock.writeLock().lock();
          log.debug("[{}]: Add to downloaded '{}'", Thread.currentThread().getName(),
              node.getUrl());
          downloadedUrls.put(node.getUrl(), document);
          downloadedUrlsLock.writeLock().unlock();
        }
      }
    }

    for (Node waiter : waiters) {
      waiter.setDocument(document);
      log.debug("[{}]: Invoke call back for '{}'", Thread.currentThread().getName(),
          waiter.getUrl());
      callBack.accept(waiter);
    }
  }

  private ArrayList<Node> initWaiters(Node node) {
    return new ArrayList<Node>() {{
      add(node);
    }};
  }

  private void handlerIOException(Node node) {

  }

}
