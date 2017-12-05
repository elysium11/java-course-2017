package ru.ilnurkhafizoff;

import info.kgeorgiy.java.advanced.crawler.Document;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import info.kgeorgiy.java.advanced.crawler.URLUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadHandler {

  Logger logger = LoggerFactory.getLogger(DownloadHandler.class);

  private final ConcurrentMap<String, Semaphore> perHostDownloadSemaphors = new ConcurrentHashMap<>();

  private final Downloader downloader;
  private final int perHostMax;

  public DownloadHandler(Downloader downloader, int perHostMax) {
    this.downloader = downloader;
    this.perHostMax = perHostMax;
  }

  public Document download(String url) throws IOException, InterruptedException {
    String host = URLUtils.getHost(url);

    perHostDownloadSemaphors.computeIfAbsent(host, h -> new Semaphore(perHostMax));

    Document document;
    try {
      logger.debug("Try acquire sem for '{}' url", url);
      perHostDownloadSemaphors.get(host).acquire();
      logger.debug("Acquired sem for '{}' url", url);

      logger.debug("Download '{}' url", url);
      document = downloader.download(url);
    } finally {
      perHostDownloadSemaphors.get(host).release();
      logger.debug("Download '{}' url", url);
    }

    return document;
  }
}
