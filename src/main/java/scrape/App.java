package scrape;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

// Minimal scraper for retrieving invoice detail for MY gas provider.
// Striving for simplicity.
@Slf4j
public class App {

  public static void main(String[] args) {
    try {
      run(args);
    } catch (IOException | InvalidFormatException e) {
      log.error("Scrape failed.", e);
      System.exit(1);
    }
  }

  public static void run(String[] args) throws IOException, InvalidFormatException {
    if (args.length < 2) {
      throw new IllegalArgumentException("Missing arguments: filename path...");
    }

    CSV data = new CSV(args[0]);
    String maxDate = data.maxValue(0);

    for (int i = 1; i < args.length; i++) {
      Path path = Paths.get(args[i]);
      byte[] xls = Files.readAllBytes(path);

      CSV csv = CSVUtils.convertExcelToCSV(xls);

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
      csv.appendTo(args[0]);
    }
  }
}
