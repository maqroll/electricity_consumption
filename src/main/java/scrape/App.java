package scrape;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

// Minimal scraper for retrieving invoice detail for MY gas provider.
// Striving for simplicity.
public class App {

  public static void main(String[] args) {
    try {
      run(args);
    } catch (IOException | InvalidFormatException e) {
      System.exit(1);
    }
  }

  public static void run(String[] args) throws IOException, InvalidFormatException {
    if (args.length < 3) {
      throw new IllegalArgumentException(
          "Missing arguments (user and/or password and/or filename)");
    }

    CSV data = new CSV(args[2]);
    String maxDate = data.maxValue(0);

    Scrape scrape = new Scrape(args[0] /* user */, args[1] /* password */);
    scrape.searchInvoicesAfter(LocalDate.parse(maxDate, DateTimeFormatter.ofPattern("yyyy/MM/dd")));

    while (scrape.hasMoreInvoices()) {
      CSV csv = scrape.nextInvoice();
      csv.getRidOfColumn(2); // TO column
      csv.transformColumn(
          0,
          v -> { // day/month/year -> year/month/day
            String[] values = v.split("/");
            return values[2] + "/" + values[1] + "/" + values[0];
          });
      csv.transformColumn(
          2,
          v -> {
            String res;
            switch (v) {
              case "Energía Activa Valle":
                res = "Valle";
                break;
              case "Energía Activa Punta":
                res = "Punta";
                break;
              case "Energía Activa Llano":
                res = "Llano";
                break;
              default:
                throw new IllegalStateException("Value " + v + " not recognized");
            }
            return res;
          });
        csv.appendTo(args[2]);
    }
  }
}
