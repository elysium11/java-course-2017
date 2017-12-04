import info.kgeorgiy.java.advanced.crawler.Document;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import info.kgeorgiy.java.advanced.crawler.URLUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class DownloadHandler {

  private final Map<String, Semaphore> perHostDownloadingNum = new HashMap<>();

  private final Downloader downloader;
  private final int perHostMax;

  public DownloadHandler(Downloader downloader, int perHostMax) {
    this.downloader = downloader;
    this.perHostMax = perHostMax;
  }

  public Document download(String url) throws IOException, InterruptedException {
    String host = URLUtils.getHost(url);

    if (!perHostDownloadingNum.containsKey(host)) {
      synchronized (perHostDownloadingNum) {
        perHostDownloadingNum.putIfAbsent(host, new Semaphore(perHostMax));
      }
    }

    perHostDownloadingNum.get(host).acquire();

    Document document = downloader.download(url);

    perHostDownloadingNum.get(host).release();

    return document;
  }
}
