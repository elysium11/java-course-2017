import info.kgeorgiy.java.advanced.crawler.Crawler;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import info.kgeorgiy.java.advanced.crawler.Result;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//todo: polish shutdown, change setError() to processed(error), add main
public class WebCrawlerV2 implements Crawler {

  private static final Logger log = LoggerFactory.getLogger(WebCrawler.class);

  private final ExecutorService downloadExecutors;
  private final ExecutorService extractExecutors;

  private final DownloadHandler downloadHandler;

  public WebCrawlerV2(Downloader downloader, int downloaders, int extractors, int perHost) {
    log.debug("Create WebCrawler with {} downloaders threads and {} extractors threads {}",
        downloaders, extractors);

    downloadExecutors = Executors
        .newFixedThreadPool(downloaders, new NamingThreadFactory("DownloaderThread-%s"));
    extractExecutors = Executors
        .newFixedThreadPool(extractors, new NamingThreadFactory("ExtractorThread-%s"));

    this.downloadHandler = new DownloadHandler(downloader, perHost);
  }

  @Override
  public Result download(String url, int depth) {
    UrlProcessor urlProcessor = new UrlProcessor(extractExecutors, downloadExecutors,
        downloadHandler);
    try {
      return urlProcessor.processUrl(url, depth);
    } catch (InterruptedException e) {
      System.out.println("Interrupted...");
      return new Result(Collections.emptyList(), Collections.emptyMap());
    }
  }

  @Override
  public void close() {
    extractExecutors.shutdown();
    downloadExecutors.shutdown();
  }
}
