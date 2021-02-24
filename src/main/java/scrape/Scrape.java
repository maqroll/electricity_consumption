package scrape;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Scrape {
  private static Logger logger = LoggerFactory.getLogger(Scrape.class);

  private static final String DOMAIN = "https://areaclientes.comercializadoraregulada.es";
  private static final String URL_PREFIX = DOMAIN + "/ovh-web";
  private static final String LOGIN_URL = URL_PREFIX + "/Login.gas";
  private static final String SEARCH_INVOICES_URL =
      URL_PREFIX + "/SearchInvoiceAndConsumptionList.gas";

  private static final String JSESSIONID_COOKIE = "JSESSIONID_OV";
  public static final int TIMEOUT = 60000;
  private final String user;
  private final String password;
  private final Deque<String> invoicesCsvUrl = new LinkedList<>();
  private String authCookie = "";

  public Scrape(String user, String password) {
    this.user = user;
    this.password = password;
  }

  public void searchInvoicesAfter(LocalDate maxDatePresent)
      throws IOException, InvalidFormatException {
    logger.info("Retrieving invoices with max date {}", maxDatePresent.toString());
    authCookie = login();

    showInvoicesForm(authCookie);

    searchInvoicesAfter(authCookie);

    List<Map.Entry<LocalDate, String>> invoices = new ArrayList<>();

    // just 14 months, 3 pages
    for (int i = 0; i <= 2; i++) {
      invoices.addAll(getInvoicesResultPage(authCookie, i));
    }

    for (Map.Entry<LocalDate, String> invoiceAndDate : invoices) {
      // process invoices with emission date at least 20 days later than max present date
      if (maxDatePresent == null || maxDatePresent.plusDays(20).isBefore(invoiceAndDate.getKey())) {
        logger.info("Processing invoice with date {}", invoiceAndDate.getKey().toString());
        String csv = getInvoiceDetail(authCookie, invoiceAndDate.getValue());
        if (csv != null) { // from most recent to last recent
          invoicesCsvUrl.addLast(csv);
        }
      } else {
        logger.warn("Ignoring invoice with date {}", invoiceAndDate.getKey().toString());
      }
    }
  }

  private byte[] getCsv(String authCookie, String url) throws IOException {
    Connection.Response res =
        Jsoup.connect(DOMAIN + url)
            .timeout(TIMEOUT)
            .cookie(JSESSIONID_COOKIE, authCookie)
            .followRedirects(true)
            .ignoreContentType(true)
            .execute();

    return res.bodyAsBytes();
  }

  private String getInvoiceDetail(String authCookie, String url) throws IOException {
    String res = null;

    Document invoiceDetail =
        Jsoup.connect(DOMAIN + url)
            .timeout(TIMEOUT)
            .cookie(JSESSIONID_COOKIE, authCookie)
            .followRedirects(true)
            .get();

    Elements links = invoiceDetail.getElementsByTag("a");
    for (Element link : links) {
      String href = link.attr("href");
      if (href.startsWith("/ovh-web/TimeCurve.gas")) {
        res = href;
        break; // just one
      }
    }

    return res;
  }

  private static LocalDate parseDate(String date) {
    return LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
  }

  private List<Map.Entry<LocalDate, String>> getInvoicesResultPage(
      String authCookie, int pageNumber) throws IOException {
    List<Map.Entry<LocalDate, String>> invoices = new ArrayList<>();

    Document invoicesResult =
        Jsoup.connect(SEARCH_INVOICES_URL)
            .timeout(TIMEOUT)
            .cookie(JSESSIONID_COOKIE, authCookie)
            .data("pagefacturasEmitidas", Integer.toString(pageNumber))
            .followRedirects(true)
            .get();

    Elements links = invoicesResult.getElementsByTag("a");
    for (Element link : links) {
      String href = link.attr("href");
      if (href.startsWith("/ovh-web/InvoiceDetail.gas")) {
        Elements date = link.getElementsByClass("factura-fecha");
        if (date.isEmpty()) {
          throw new IllegalStateException(
              "Couldn't find factura-fecha element inside invoiceDetail link");
        }
        if (date.size() > 1) {
          throw new IllegalStateException(
              "Found more than one factura-fecha element inside invoiceDetail link");
        }
        invoices.add(new AbstractMap.SimpleEntry<>(parseDate(date.text()), href));
      }
    }

    return invoices;
  }

  private void showInvoicesForm(String authCookie) throws IOException {
    Connection invoices = Jsoup.connect(SEARCH_INVOICES_URL);
    Document invoicesResult =
        invoices.timeout(TIMEOUT).cookie(JSESSIONID_COOKIE, authCookie).followRedirects(true).get();
  }

  private void searchInvoicesAfter(String authCookie) throws IOException {
    Connection searchInvoices = Jsoup.connect(SEARCH_INVOICES_URL);
    Connection.Response searchInvoicesResult =
        searchInvoices
            .timeout(TIMEOUT)
            .data("beforeDate", "01/01/2020")
            .data("afterDate", "")
            .data("productType", "all")
            .data("b-buscar", "Buscar")
            .cookie(JSESSIONID_COOKIE, authCookie)
            .followRedirects(true)
            .method(Connection.Method.POST)
            .execute();
  }

  private String login() throws IOException {
    Connection login = Jsoup.connect(LOGIN_URL);
    Connection.Response loginResponse =
        login
            .data("username", user)
            .data("password", password)
            .data("submitBtn", "enterBtn")
            .data("enterBtn", "Entra")
            .method(Connection.Method.POST)
            .followRedirects(true)
            .execute();

    return loginResponse.cookie(JSESSIONID_COOKIE);
  }

  public boolean hasMoreInvoices() {
    return !invoicesCsvUrl.isEmpty();
  }

  public CSV nextInvoice() throws IOException, InvalidFormatException {
    String csvUrl = invoicesCsvUrl.pollLast(); // less recent
    if (csvUrl != null) {
      byte[] data = getCsv(authCookie, csvUrl);
      return CSVUtils.convertExcelToCSV(data);
    } else {
      return null;
    }
  }
}
