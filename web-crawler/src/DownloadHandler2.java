import info.kgeorgiy.java.advanced.crawler.Document;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadHandler2 {

  private static final Logger logger = LoggerFactory.getLogger(DownloadHandler2.class);

  private final Set<String> downloadedUrls = new HashSet<>();
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
  private final Downloader downloader;

  public DownloadHandler2(Downloader downloader) {
    this.downloader = downloader;
  }

  public void download(Node node, Consumer<? super Node> callBack) {
    logger.debug("Url '{}' starting download processing", node.getUrl());
    boolean downloadedAlready;

    rwLock.readLock().lock();
    downloadedAlready = downloadedUrls.contains(node.getUrl());
    rwLock.readLock().unlock();

    if (!downloadedAlready) {
      rwLock.writeLock().lock();
      downloadedAlready = downloadedUrls.contains(node.getUrl());
      if (!downloadedAlready) {
        logger.debug("Url '{}' add to download already", node.getUrl());
        downloadedUrls.add(node.getUrl());
      }
      rwLock.writeLock().unlock();
    }

    if (!downloadedAlready) {
      try {
        logger.debug("Url '{}' download", node.getUrl());
        Document document = downloader.download(node.getUrl());
        node.setDocument(document);
        logger.debug("Url '{}' callback invoke", node.getUrl());
        callBack.accept(node);
      } catch (IOException e) {
        node.setError(e);
        node.processed();
      }
    } else {
      logger.debug("Already downloaded url {}", node.getUrl());
      node.processed(true);
    }
  }
}
