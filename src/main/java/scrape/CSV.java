package scrape;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;
import java.util.stream.Collectors;

// Very basic CSV representation
public class CSV {
  private static final String RECORD_SEP = "\n";
  private static final String FIELD_SEP = ",";
  private final List<List<String>> data = new ArrayList<>();
  private final boolean firstRowIsHeader;

  public CSV(String filename) throws FileNotFoundException {
    File file = new File(".", filename);
    this.firstRowIsHeader = file.exists();
    load(file);
  }

  public CSV(boolean firstRowIsHeader) {
    this.firstRowIsHeader = firstRowIsHeader;
  }

  // Assumes no need to escape , inside values.
  private void load(File file) throws FileNotFoundException {
    if (file.exists()) {
      try (Scanner scanner = new Scanner(file)) {
        scanner.useDelimiter(RECORD_SEP);
        while (scanner.hasNext()) {
          String row = scanner.next();
          String[] fields = row.split(FIELD_SEP);
          data.add((List<String>) Arrays.asList(fields));
        }
      }
    }
  }

  public void addRow(List<String> row) {
    data.add(row);
  }

  public void getRidOfColumn(int col) {
    for (List<String> row : data) {
      if (row.size() > col) { // ignore remove on rows without enough elements
        row.remove(col);
      }
    }
  }

  public String toString(boolean skipHeader) {
    return data.stream()
            .skip(firstRowIsHeader && skipHeader ? 1 : 0)
            .map(row -> String.join(FIELD_SEP, row))
            .collect(Collectors.joining(RECORD_SEP))
        + RECORD_SEP;
  }

  public void appendTo(String filename) throws IOException {
    File file = new File(".", filename);

    if (file.exists()) {
      // If file exists assume header is already there
      Files.write(file.toPath(), toString(true).getBytes(), StandardOpenOption.APPEND);
    } else {
      Files.write(
          file.toPath(),
          toString(false).getBytes(),
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    }
  }

  public void transformColumn(int col, Function<String, String> t) {
    for (int rowNumber = 0; rowNumber < data.size(); rowNumber++) {
      if (firstRowIsHeader && rowNumber == 0) continue;
      List<String> row = data.get(rowNumber);
      if (row.size() > col) { // ignore remove on rows without enough elements
        row.set(col, t.apply(row.get(col)));
      }
    }
  }

  public String maxValue(int col) {
    Optional<String> max =
        data.stream()
            .skip(firstRowIsHeader ? 1 : 0)
            .map((row) -> row.get(col))
            .max(Comparator.naturalOrder());

    return max.orElse("1970/01/01");
  }
}
