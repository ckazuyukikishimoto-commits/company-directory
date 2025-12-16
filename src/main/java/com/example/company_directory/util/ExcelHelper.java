package com.example.company_directory.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.example.company_directory.dto.ImportRowDto;
import com.example.company_directory.entity.Company;

public class ExcelHelper {

    public static ByteArrayInputStream companiesToExcel(List<Company> companies) {
        return companiesToExcel(companies, null);
    }

    public static ByteArrayInputStream companiesToExcel(List<Company> companies, List<String> columnKeys) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Companies");

        // Column Definitions (Order matters)
        Map<String, String> headersMap = new LinkedHashMap<>();
        headersMap.put("companyId", "企業ID");
        headersMap.put("companyName", "企業名");
        headersMap.put("address", "住所");
        headersMap.put("zipCode", "郵便番号");
        headersMap.put("registrationDate", "登録日");
        headersMap.put("remarks", "備考");

        // Determine active columns
        List<String> activeKeys = new ArrayList<>();
        if (columnKeys == null || columnKeys.isEmpty()) {
            activeKeys.addAll(headersMap.keySet());
        } else {
            // Keep definition order but filter by selected
            for (String key : headersMap.keySet()) {
                if (columnKeys.contains(key)) {
                    activeKeys.add(key);
                }
            }
        }

        // ----- ヘッダー行 -----
        XSSFRow hRow = sheet.createRow(0);
        int colIdx = 0;
        hRow.createCell(colIdx++).setCellValue("No.");
        for (String key : activeKeys) {
            hRow.createCell(colIdx++).setCellValue(headersMap.get(key));
        }

        // ----- データ行 -----
        int rNum = 1;
        for (Company company : companies) {
            XSSFRow row = sheet.createRow(rNum);
            colIdx = 0;
            row.createCell(colIdx++).setCellValue(rNum); // No.列

            for (String key : activeKeys) {
                Cell cell = row.createCell(colIdx++);
                switch (key) {
                    case "companyId":
                        cell.setCellValue(company.getCompanyId());
                        break;
                    case "companyName":
                        cell.setCellValue(company.getCompanyName());
                        break;
                    case "address":
                        cell.setCellValue(company.getAddress());
                        break;
                    case "zipCode":
                        cell.setCellValue(company.getZipCode());
                        break;
                    case "registrationDate":
                        cell.setCellValue(company.getRegistrationDate().toString());
                        break;
                    case "remarks":
                        cell.setCellValue(company.getRemarks());
                        break;
                }
            }
            rNum++;
        }

        // ----- Excelファイルを ByteArray に書き込む -----
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            workbook.close();

            return new ByteArrayInputStream(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Excel作成でエラーが発生", e);
        }
    }

    public static List<ImportRowDto> parseExcel(InputStream inputStream) {
        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(inputStream);
        } catch (Exception e) {
            throw new RuntimeException(analyzeWorkbookCreationError(e), e);
        }

        List<ImportRowDto> rows = new ArrayList<>();

        try (workbook) {

            // --- シート存在チェック ---
            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException("シートが存在しません。正しいテンプレートを使用してください。");
            }

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new RuntimeException("指定のシートが見つかりません。");
            }

            // --- シート空チェック ---
            if (sheet.getPhysicalNumberOfRows() <= 1) {
                throw new RuntimeException("シートにデータがありません。");
            }

            // --- 行数制限（メモリ保護） ---
            if (sheet.getLastRowNum() > 50000) {
                throw new RuntimeException("データ量が大きすぎて読み込めません。ファイルを分割してください。");
            }

            DataFormatter formatter = new DataFormatter();

            Iterator<Row> rowIterator = sheet.iterator();
            if (!rowIterator.hasNext()) {
                throw new RuntimeException("ヘッダー行が存在しません。テンプレートを使用してください。");
            }

            // --- ヘッダー読み取り ---
            Row header = rowIterator.next();
            Map<String, Integer> columnIndex = new HashMap<>();

            short lastCellNum = header.getLastCellNum();
            if (lastCellNum <= 0) {
                throw new RuntimeException("ヘッダー行が空です。テンプレートを確認してください。");
            }

            for (int i = 0; i < header.getLastCellNum(); i++) {
                // BOM除去と全角スペース対応（Java 11以降はstrip()推奨）
                String rawValue = formatter.formatCellValue(header.getCell(i));
                String colName = rawValue.replace("\uFEFF", "").strip();

                if (!colName.isEmpty()) {
                    columnIndex.put(colName, i);
                }
            }

            // --- 必須列チェック ---
            String[] requiredColumns = { "企業名", "住所", "郵便番号" };

            List<String> missingColumns = new ArrayList<>();

            for (String col : requiredColumns) {
                if (!columnIndex.containsKey(col)) {
                    missingColumns.add(col);
                }
            }

            if (!missingColumns.isEmpty()) {
                throw new RuntimeException(
                        "必須列が不足しています：" + String.join("、", missingColumns) +
                                " (検出された列名: " + String.join(", ", columnIndex.keySet()) + ")");
            }

            int rowNum = 0;

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                rowNum++;

                if (row == null)
                    continue;

                // --- 空行判定 ---
                boolean allEmpty = true;
                for (int i = 0; i < header.getLastCellNum(); i++) {
                    String v = safeFormatCell(row, i, formatter);
                    if (v != null && !v.trim().isEmpty()) {
                        allEmpty = false;
                        break;
                    }
                }
                if (allEmpty)
                    continue;

                // --- DTO 作成 ---
                ImportRowDto dto = new ImportRowDto();
                dto.setRowNum(rowNum);

                dto.setCompanyId(getValue(row, columnIndex, "企業ID", formatter));
                dto.setCompanyName(getValue(row, columnIndex, "企業名", formatter));
                dto.setAddress(getValue(row, columnIndex, "住所", formatter));

                // 郵便番号の正規化（全角ハイフン対応）
                String rawZip = getValue(row, columnIndex, "郵便番号", formatter);
                dto.setZipCode(normalizeZipCode(rawZip));

                dto.setRegistrationDate(getValue(row, columnIndex, "登録日", formatter));
                dto.setRemarks(getValue(row, columnIndex, "備考", formatter));

                rows.add(dto);
            }

        } catch (Exception e) {
            throw new RuntimeException("Excel読み取り中にエラーが発生しました: " + e.getMessage(), e);
        }

        return rows;
    }

    private static String analyzeWorkbookCreationError(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (message.contains("zip") || message.contains("header") || message.contains("signature")) {
            return "ファイル形式が不正です。Excelファイル（.xlsx/.xls）であることを確認してください。";
        } else if (message.contains("password") || message.contains("encrypted")) {
            return "パスワード保護されたファイルは読み込めません。保護を解除してください。";
        } else if (message.contains("memory") || message.contains("heap") || message.contains("outofmemory")) {
            return "ファイルが大きすぎて読み込めません。ファイルを分割してください。";
        } else if (message.contains("corrupt") || message.contains("invalid")) {
            return "ファイルが破損している可能性があります。正しいExcelファイルをアップロードしてください。";
        } else {
            return "ファイルを読み込めません。Excelファイルが壊れている可能性があります。";
        }
    }

    /** セル値を安全に取得（セル結合・破損対策） */
    private static String safeFormatCell(Row row, int index, DataFormatter formatter) {
        try {
            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

            if (cell == null) {
                return "";
            }

            // ★ 修正ポイント：セルが日付の場合は LocalDate に変換して統一
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                return date.toString(); // 例: "2020-05-26"
            }

            // 数値セル（整数、小数）もズレを避けるため専用処理
            if (cell.getCellType() == CellType.NUMERIC) {
                return formatter.formatCellValue(cell);
            }

            // 文字列は従来通り
            return formatter.formatCellValue(cell);

        } catch (Exception ex) {
            throw new RuntimeException(
                    String.format("セル書式が破損しています。（行: %d, 列: %d）セル結合の解除や再保存を試してください。",
                            row.getRowNum() + 1, index + 1));
        }
    }

    /** 列名が存在すれば取得 */
    private static String getValue(Row row, Map<String, Integer> columnIndex,
            String columnName, DataFormatter formatter) {
        if (!columnIndex.containsKey(columnName)) {
            return null;
        }
        int idx = columnIndex.get(columnName);
        return safeFormatCell(row, idx, formatter);
    }

    // ExcelHelper.java

    public static ByteArrayInputStream dtosToExcel(List<ImportRowDto> rows) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Errors_Warnings");

        // ヘッダー作成 (元のフォーマット + 理由列)
        String[] headers = { "行番号", "企業ID", "企業名", "住所", "郵便番号", "登録日", "備考", "エラー・警告内容" };
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        // データ作成
        int rowIdx = 1;
        for (ImportRowDto dto : rows) {
            Row row = sheet.createRow(rowIdx++);

            // 元のデータを出力
            row.createCell(0).setCellValue(dto.getRowNum());
            row.createCell(1).setCellValue(dto.getCompanyId());
            row.createCell(2).setCellValue(dto.getCompanyName());
            row.createCell(3).setCellValue(dto.getAddress());
            row.createCell(4).setCellValue(dto.getZipCode());
            row.createCell(5).setCellValue(dto.getRegistrationDate());
            row.createCell(6).setCellValue(dto.getRemarks());

            // ★エラー・警告メッセージを結合して出力
            List<String> msgs = new ArrayList<>();
            if (dto.getErrorMessages() != null)
                msgs.addAll(dto.getErrorMessages());
            if (dto.getWarningMessages() != null)
                msgs.addAll(dto.getWarningMessages());

            row.createCell(7).setCellValue(String.join(" / ", msgs));
        }

        // ----- Excelファイルを ByteArray に書き込む -----
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            workbook.close();

            return new ByteArrayInputStream(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Excel作成でエラーが発生", e);
        }
    }

    public static ByteArrayInputStream createTemplate() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("インポート用テンプレート");

        // ヘッダー行の作成
        Row headerRow = sheet.createRow(0);

        // 必須および任意の列名（仕様書に合わせて定義）
        String[] headers = { "企業ID", "企業名", "住所", "郵便番号", "登録日", "備考" };

        // スタイル設定（黄色い背景などで目立たせると親切ですが、まずは標準でOK）
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
            // カラム幅を自動調整（データがないとうまく動かないこともありますが念のため）
            sheet.setColumnWidth(i, 4000);
        }

        // 入力例としてコメントを入れたり、2行目に例を入れるのもアリですが、
        // まずは「ヘッダーのみ」で返します。

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            workbook.close();
            return new ByteArrayInputStream(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("テンプレート作成中にエラーが発生しました", e);
        }
    }

    /** 郵便番号の正規化（全角ハイフンを半角に、全角数字を半角に） */
    private static String normalizeZipCode(String input) {
        if (input == null)
            return null;

        // ハイフンの正規化
        String normalized = input.replace("ー", "-").replace("－", "-");

        // 全角数字を半角数字に変換
        StringBuilder sb = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (c >= '０' && c <= '９') {
                sb.append((char) (c - '０' + '0'));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

}