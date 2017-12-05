package ru.ilnurkhafizoff;

import info.kgeorgiy.java.advanced.crawler.Document;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class Node {

  private final String url;
  private final int nodeDepth;
  private final int maxDepth;

  private volatile Document document;
  private volatile List<Node> childs = new ArrayList<>();

  private IOException error;
  private boolean processed = false;
  private boolean repeated = false;
  private boolean interrupted = false;

  private final Object lock = new Object();

  public Node(String url, int nodeDepth, int maxDepth) {
    this.url = url;
    this.nodeDepth = nodeDepth;
    this.maxDepth = maxDepth;
  }

  public String getUrl() {
    return url;
  }

  public Document getDocument() {
    return document;
  }

  public void setDocument(Document document) {
    this.document = document;
  }

  public List<Node> getChilds() {
    return childs;
  }

  public void setChilds(List<Node> childs) {
    this.childs = childs;
  }

  public IOException getError() {
    return error;
  }

  public int getNodeDepth() {
    return nodeDepth;
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public boolean isInterrupted() {
    return interrupted;
  }

  public boolean isLeafNode() {
    return nodeDepth == maxDepth;
  }


  public boolean notProcessed() {
    return !processed;
  }

  public void processed(boolean isRepeated) {
    synchronized (lock) {
      processed = true;
      repeated = isRepeated;
      lock.notify();
    }
  }

  public void interrupted() {
    synchronized (lock) {
      processed = true;
      interrupted = true;
      lock.notify();
    }
  }

  public void processed() {
    synchronized (lock) {
      processed = true;
      repeated = false;
      lock.notify();
    }
  }

  public void processed(IOException e) {
    synchronized (lock) {
      processed = true;
      repeated = false;
      error = e;
      lock.notify();
    }
  }

  public void waitProcessing() throws InterruptedException {
    synchronized (lock) {
      while (notProcessed()) {
        lock.wait();
      }
    }
  }

  public boolean isRepeated() {
    return repeated;
  }

  @Override
  public String toString() {
    return "Node{" +
        "url='" + url + '\'' +
        ", nodeDepth=" + nodeDepth +
        ", maxDepth=" + maxDepth +
        ", error=" + error +
        ", processed=" + processed +
        ", repeated=" + repeated +
        ", interrupted=" + interrupted +
        '}';
  }
}
