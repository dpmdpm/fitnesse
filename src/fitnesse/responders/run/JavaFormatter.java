package fitnesse.responders.run;

import fitnesse.responders.run.formatters.BaseFormatter;
import fitnesse.wiki.WikiPage;
import fitnesse.wiki.WikiPagePath;
import util.TimeMeasurement;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Used to run tests from a JUnit test suite.
 *
 * @see {@link fitnesse.junit.FitNesseSuite}
 */
public class JavaFormatter extends BaseFormatter {

  private String mainPageName;
  private boolean isSuite = true;
  public static final String SUMMARY_FOOTER = "</table>";
  public static final String SUMMARY_HEADER = "<table><tr><td>Name</td><td>Right</td><td>Wrong</td><td>Exceptions</td></tr>";

  public interface ResultsRepository {
    void open(String string) throws IOException;

    void close() throws IOException;

    void write(String content) throws IOException;
  }

  public static class FileCopier {
    public static void copy(String src, File dst) throws IOException {
      InputStream in = FileCopier.class.getResourceAsStream(src);
      OutputStream out = new FileOutputStream(dst);
      // Transfer bytes from in to out
      byte[] buf = new byte[1024];
      int len;
      while ((len = in.read(buf)) > 0) {
        out.write(buf, 0, len);
      }
      in.close();
      out.close();
    }
  }

  public static class TestResultPage {
    private OutputStreamWriter currentWriter;

    public TestResultPage(String outputPath, String testName) throws IOException, UnsupportedEncodingException {
      File outputFile = new File(outputPath, testName + ".html");
      currentWriter = new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8");
      writeHeaderFor(testName);
    }

    public void appendResultChunk(String content) throws IOException {
      currentWriter.write(content.replace("src=\"/files/images/", "src=\"images/"));
    }

    private void writeHeaderFor(String testName) throws IOException {
      currentWriter.write("<html><head><title>");
      currentWriter.write(testName);
      currentWriter
              .write("</title><meta http-equiv='Content-Type' content='text/html;charset=utf-8'/>"
                      + "<link rel='stylesheet' type='text/css' href='fitnesse.css'/>"
                      + "<script src='fitnesse.js' type='text/javascript'></script>" + "</head><body><h2>");
      currentWriter.write(testName);
      currentWriter.write("</h2>");
    }

    public void finish() throws IOException {
      if (currentWriter != null) {
        currentWriter.write("</body></html>");
        currentWriter.close();
      }
    }
  }
  
  public static class FolderResultsRepository implements ResultsRepository {
    private String outputPath;
    private TestResultPage testResultPage;

    public FolderResultsRepository(String outputPath) throws IOException {
      this.outputPath = outputPath;
      copyAssets();
    }

    public void close() throws IOException {
      testResultPage.finish();
    }

    public void open(String testName) throws IOException {
      testResultPage = new TestResultPage(outputPath, testName);

    }

    public void write(String content) throws IOException {
      testResultPage.appendResultChunk(content);
    }

    public void addFile(String r, String relativeFilePath) throws IOException {
      File dst = new File(outputPath, relativeFilePath);
      dst.getParentFile().mkdirs();
      FileCopier.copy(r, dst);
    }

    private void copyAssets() throws IOException {
      String base = "/fitnesse/resources/";
      String cssDir = base + "css/";
      addFile(cssDir + "fitnesse_wiki.css", "fitnesse.css");
      String javascriptDir = base + "javascript/";
      addFile(javascriptDir + "fitnesse.js", "fitnesse.js");
      String imagesDir = base + "images/";
      addFile(imagesDir + "collapsibleOpen.png", "images/collapsibleOpen.png");
      addFile(imagesDir + "collapsibleClosed.png", "images/collapsibleClosed.png");
    }
  }

  private TestSummary totalSummary = new TestSummary();

  public String getFullPath(final WikiPage wikiPage) {
    return new WikiPagePath(wikiPage).toString();
  }

  private List<String> visitedTestPages = new ArrayList<String>();
  private Map<String, TestSummary> testSummaries = new HashMap<String, TestSummary>();

  @Override
  public void newTestStarted(TestPage test, TimeMeasurement timeMeasurement) throws IOException {
    resultsRepository.open(getFullPath(test.getSourcePage()));
    if (listener != null)
      listener.newTestStarted(test, timeMeasurement);
  }

  @Override
  public void setExecutionLogAndTrackingId(String stopResponderId, CompositeExecutionLog log) {
  }

  public void testComplete(TestPage test, TestSummary testSummary, TimeMeasurement timeMeasurement) throws IOException {
    String fullPath = getFullPath(test.getSourcePage());
    visitedTestPages.add(fullPath);
    totalSummary.add(testSummary);
    testSummaries.put(fullPath, new TestSummary(testSummary));
    resultsRepository.close();
    isSuite = isSuite && (!mainPageName.equals(fullPath));
    if (listener != null)
      listener.testComplete(test, testSummary, timeMeasurement);
  }

  TestSummary getTestSummary(String testPath) {
    return testSummaries.get(testPath);
  }

  @Override
  public void testOutputChunk(String output) throws IOException {
    resultsRepository.write(output);
  }

  @Override
  public void testSystemStarted(TestSystem testSystem, String testSystemName, String testRunner) {
  }

  private ResultsRepository resultsRepository;

  public TestSummary getTotalSummary() {
    return totalSummary;
  }

  public void setTotalSummary(TestSummary testSummary) {
    totalSummary = testSummary;
  }

  public void setResultsRepository(ResultsRepository mockResultsRepository) {
    this.resultsRepository = mockResultsRepository;

  }

  /** package-private to prevent instantiation apart from getInstance and tests */
  JavaFormatter(String suiteName) {
    this.mainPageName = suiteName;
  }

  private static Map<String, JavaFormatter> allocatedInstances = new HashMap<String, JavaFormatter>();
  private ResultsListener listener;

  public synchronized static JavaFormatter getInstance(String testName) {
    JavaFormatter existing = allocatedInstances.get(testName);
    if (existing != null)
      return existing;
    existing = new JavaFormatter(testName);
    allocatedInstances.put(testName, existing);
    return existing;
  }

  @Override
  public void allTestingComplete(TimeMeasurement totalTimeMeasurement) throws IOException {
    if (isSuite)
      writeSummary(mainPageName);
    if (listener != null)
      listener.allTestingComplete(totalTimeMeasurement);
  }

  public void writeSummary(String suiteName) throws IOException {
    resultsRepository.open(suiteName);
    resultsRepository.write(SUMMARY_HEADER);
    for (String s : visitedTestPages) {
      resultsRepository.write(summaryRow(s, testSummaries.get(s)));
    }
    resultsRepository.write(SUMMARY_FOOTER);
    resultsRepository.close();
  }

  public String summaryRow(String testName, TestSummary testSummary) {
    StringBuffer sb = new StringBuffer();
    sb.append("<tr class=\"").append(getCssClass(testSummary)).append("\"><td>").append(
        "<a href=\"").append(testName).append(".html\">").append(testName).append("</a>").append(
        "</td><td>").append(testSummary.right).append("</td><td>").append(testSummary.wrong)
        .append("</td><td>").append(testSummary.exceptions).append("</td></tr>");
    return sb.toString();
  }

  private String getCssClass(TestSummary ts) {
    if (ts.exceptions > 0)
      return "error";
    if (ts.wrong > 0)
      return "fail";
    if (ts.right > 0)
      return "pass";
    return "plain";
  }

  public void setListener(ResultsListener listener) {
    this.listener = listener;
  }

  public List<String> getTestsExecuted() {
    return visitedTestPages;
  }

  public static void dropInstance(String testName) {
    allocatedInstances.remove(testName);
  }

}
