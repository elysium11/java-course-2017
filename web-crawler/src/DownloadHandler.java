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

  public static final Document ERROR_DOCUMENT = Collections::emptyList;

  private final Map<String, List<Node>> waitingForDownload = new HashMap<>();
  private final Map<String, Document> downloadedUrls = new HashMap<>();
  private final ReadWriteLock downloadedUrlsLock = new ReentrantReadWriteLock();

  private final Downloader downloader;

  public DownloadHandler(Downloader downloader) {
    this.downloader = downloader;
  }

  public void download(Node node, Consumer<? super Node> callBack) {
//    Document document;
    List<Node> waiters = null;

    log.debug("Starting download url '{}'", node.getUrl());

//    document = searchInAlreadyDownloaded(node);
//    if (document != null) {
//      log.debug("Found in already downloaded => return with result '{}'", node.getUrl()
//      );
//      node.setDocument(document);
//      callBack.accept(node);
//      return;
//    }

    downloadedUrlsLock.readLock().lock();
    Document document = downloadedUrls.get(node.getUrl());
    if (document == null) {
      downloadedUrlsLock.writeLock().lock();
      downloadedUrls.put(node.getUrl(), ERROR_DOCUMENT);
      downloadedUrlsLock.writeLock().unlock();
    }
    downloadedUrlsLock.readLock().lock();



//    synchronized (waitingForDownload) {

//      document = searchInAlreadyDownloaded(node);
//      if (document != null) {
//        log.debug("Found in already downloaded => return with result '{}'", node.getUrl());
//        node.setDocument(document);
//        return;
//      }

//      log.debug("Try found node '{}' in waiting list ", node.getUrl());
//      if (waitingForDownload.containsKey(node.getUrl())) {
//        log.debug("Node '{}' found => add to waiters and return",
//
//            node.getUrl());
//        waitingForDownload.get(node.getUrl()).add(node);
//        return;
//      } else {
//        log.debug("Node '{}' not found => need to download",
//
//            node.getUrl());
//        waitingForDownload.put(node.getUrl(), initWaiters(node));
//      }
//  }

    try

    {
      log.debug("Try to download '{}'", node.getUrl());
      document = downloader.download(node.getUrl());
    } catch (
        IOException e)

    {
      log.debug("Error '{}' during download '{}'", e, node.getUrl());
      node.setError(e);
      document = ERROR_DOCUMENT;
    }

    synchronized (waitingForDownload)

    {
      log.debug("Remove url '{}' from waiters", node.getUrl());
      waiters = waitingForDownload.remove(node.getUrl());
      log.debug("List of waiters to url '{}': {}", node.getUrl(), waiters);
      downloadedUrlsLock.writeLock().lock();
      log.debug("Put to downloaded '{}'", node.getUrl());
      downloadedUrls.put(node.getUrl(), document);
      downloadedUrlsLock.writeLock().unlock();
    }

    for (
        Node waiter : waiters)

    {
      waiter.setDocument(document);
      log.debug("Invoke call back for waiter '{}'", waiter.getUrl());
      callBack.accept(waiter);
    }

  }

  Document searchInAlreadyDownloaded(Node node) {
    downloadedUrlsLock.readLock().lock();
    Document document = downloadedUrls.get(node.getUrl());
    log.debug(
        "Searching in downloaded urls result '{}'", document);
    downloadedUrlsLock.readLock().unlock();
    return document;
  }

  void addToAlreadyDownloaded(String url, Document document) {
    downloadedUrlsLock.writeLock().lock();
    log.debug("Put url '{}' to downloaded", url);
    downloadedUrls.put(url, document);
    downloadedUrlsLock.writeLock().unlock();
  }

  private ArrayList<Node> initWaiters(Node node) {
    return new ArrayList<Node>() {{
      add(node);
    }};
  }
}
