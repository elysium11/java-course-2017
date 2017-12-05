package ru.ilnurkhafizoff;

import static java.lang.Integer.parseInt;

import info.kgeorgiy.java.advanced.crawler.CachingDownloader;
import info.kgeorgiy.java.advanced.crawler.Crawler;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import info.kgeorgiy.java.advanced.crawler.Result;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebCrawler implements Crawler {

  private static final Logger log = LoggerFactory.getLogger(WebCrawler.class);

  private static final Path DOWNLOADED_URLS_DIR =
      Paths.get(System.getProperty("user.home") + File.pathSeparator + "khafizov-lab8-downloads");

  private final ExecutorService downloadExecutors;
  private final ExecutorService extractExecutors;

  private final DownloadHandler downloadHandler;

  public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
    log.debug(
        "Create ru.ilnurkhafizoff.WebCrawler with {} downloaders threads and {} extractors threads {}",
        downloaders, extractors);

    downloadExecutors = Executors
        .newFixedThreadPool(downloaders, new NamingThreadFactory("DownloaderThread-%s"));
    extractExecutors = Executors
        .newFixedThreadPool(extractors, new NamingThreadFactory("ExtractorThread-%s"));

    this.downloadHandler = new DownloadHandler(downloader, perHost);
  }

  @Override
  public Result download(String url, int depth) {
    return
        new UrlProcessor(extractExecutors, downloadExecutors, downloadHandler)
            .processUrl(url, depth);
  }

  @Override
  public void close() {
    extractExecutors.shutdown();
    downloadExecutors.shutdown();

    try {
      if (!extractExecutors.awaitTermination(20, TimeUnit.SECONDS)) {
        extractExecutors.shutdownNow();
        if (extractExecutors.awaitTermination(20, TimeUnit.SECONDS)) {
          System.err.println("Extract executor does not terminate");
        }
      }

      if (!downloadExecutors.awaitTermination(20, TimeUnit.SECONDS)) {
        downloadExecutors.shutdownNow();
        if (downloadExecutors.awaitTermination(20, TimeUnit.SECONDS)) {
          System.err.println("Download executors does not terminate");
        }
      }
    } catch (InterruptedException e) {
      extractExecutors.shutdownNow();
      downloadExecutors.shutdownNow();

      Thread.currentThread().interrupt();
    }
  }

  public static void main(String[] args) {
    try {
      CrawlingContext crawlingContext = CrawlingContext.parseArgs(args);

      try (WebCrawler webCrawler = new WebCrawler(
          new CachingDownloader(DOWNLOADED_URLS_DIR), crawlingContext.downloads,
          crawlingContext.extractors, crawlingContext.perHost
      )) {
        webCrawler.download(crawlingContext.rootUrl, 4);
      }
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }

  private static class CrawlingContext {

    private String rootUrl;
    private int downloads = 20;
    private int extractors = 20;
    private int perHost = 10;

    private CrawlingContext() {
    }

    public static CrawlingContext parseArgs(String[] args) {
      if (args.length < 1) {
        System.err.println("Too few arguments. Root url should be specified");
      }

      CrawlingContext result = new CrawlingContext();
      result.rootUrl = args[0];

      if (args.length > 1) {
        result.downloads = parseInt(args[1]);

        if (args.length > 2) {
          result.extractors = parseInt(args[2]);

          if (args.length > 3) {
            result.perHost = parseInt(args[3]);
          }
        }
      }

      return result;
    }
  }

  private static class NamingThreadFactory implements ThreadFactory {

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
}
