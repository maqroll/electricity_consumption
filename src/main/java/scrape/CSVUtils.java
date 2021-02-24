package scrape;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

// Taken from https://gist.github.com/robertchong/11072582
public class CSVUtils {

  private static final int SKIP_HEADER = 11;
  private static final int MAX_COLUMNS = 10;

  /*
   * <strong>
   * Convert Excel spreadsheet to CSV.
   * Works for <strong>Excel 97-2003 Workbook (<em>.xls)
   * </strong> and <strong>Excel Workbook (</em>.xlsx)</strong>.
   * Does not work for the <strong>XML Spreadsheet 2003 (*.xml)</strong>
   * format produced by BIRT.
   * @param fileName
   * @throws InvalidFormatException
   * @throws IOException
   *
   * @see http://bigsnowball.com/content/convert-excel-xlsx-and-xls-csv
   */
  public static CSV convertExcelToCSV(byte[] xlsData) throws InvalidFormatException, IOException {

    CSV res = new CSV(true);

    InputStream is = new ByteArrayInputStream(xlsData);

    Workbook wb = WorkbookFactory.create(is);

    Sheet sheet = wb.getSheetAt(0);

    // hopefully the first row is a header and has a full compliment of
    // cells, else you'll have to pass in a max (yuck)
    int maxColumns = /*sheet.getRow(0).getLastCellNum()*/ MAX_COLUMNS;
    boolean stopProcessingRows = false;

    for (Row row : sheet) {
      List<String> values = new ArrayList<>();

      if (row.getRowNum() < SKIP_HEADER || stopProcessingRows)
        continue; // skip first rows && skip last rows

      // row.getFirstCellNum() and row.getLastCellNum() don't return the
      // first and last index when the first or last column is blank
      int minCol = 0; // row.getFirstCellNum()
      int maxCol = maxColumns; // row.getLastCellNum()

      for (int i = minCol; i < maxCol; i++) {

        Cell cell = row.getCell(i);

        if (cell != null) {
          String v = null;

          switch (cell.getCellType()) {
            case STRING:
              v = cell.getRichStringCellValue().getString();
              break;
            case NUMERIC:
              if (DateUtil.isCellDateFormatted(cell)) {
                v = cell.getDateCellValue().toString();
              } else {
                v = String.valueOf(cell.getNumericCellValue());
              }
              break;
            case BOOLEAN:
              v = String.valueOf(cell.getBooleanCellValue());
              break;
            case FORMULA:
              v = cell.getCellFormula();
              break;
            default:
          }

          if (v != null) {
            if (v.equals("Consumo agrupado:")) {
              stopProcessingRows = true;
              break;
            }
            values.add(toCSV(v));
          }
        }
      } // cell

      if (!stopProcessingRows) {
        res.addRow(values);
      }
    } // row
    is.close();

    return res;
  }

  /*
   * </strong>
   * Escape the given value for output to a CSV file.
   * Assumes the value does not have a double quote wrapper.
   * @return
   */
  public static String toCSV(String value) {

    String v = null;
    boolean doWrap = false;

    if (value != null) {

      v = value;

      if (v.contains("\"")) {
        v = v.replace("\"", "\"\""); // escape embedded double quotes
        doWrap = true;
      }

      if (v.contains(",") || v.contains("\n")) {
        doWrap = true;
      }

      if (doWrap) {
        v = "\"" + v + "\""; // wrap with double quotes to hide the comma
      }
    }

    return v;
  }
}
