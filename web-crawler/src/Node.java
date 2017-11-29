import info.kgeorgiy.java.advanced.crawler.Document;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class Node {

  private final String url;
  private volatile Document document;
  private volatile List<Node> childs = new ArrayList<>();
  private volatile IOException error;
  private volatile int nodeDepth;
  private volatile int maxDepth;

  public Node(String url, int nodeDepth) {
    this.url = url;
    this.nodeDepth = nodeDepth;
  }

  public Node(String url, int nodeDepth, int maxDepth) {
    this(url, nodeDepth);
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

  public void addChild(Node node) {
    childs.add(node);
  }

  public IOException getError() {
    return error;
  }

  public void setError(IOException exception) {
    this.error = exception;
  }

  public int getNodeDepth() {
    return nodeDepth;
  }

  public void setNodeDepth(int nodeDepth) {
    this.nodeDepth = nodeDepth;
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public boolean isLeafNode() {
    return nodeDepth == maxDepth;
  }
}
