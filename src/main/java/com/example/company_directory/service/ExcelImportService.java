package com.example.company_directory.service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.company_directory.dto.ImportErrorDto;
import com.example.company_directory.dto.ImportResultDto;
import com.example.company_directory.dto.ImportRowDto;
import com.example.company_directory.dto.ImportWorningDto;
import com.example.company_directory.entity.Company;
import com.example.company_directory.entity.ZipMaster;
import com.example.company_directory.repository.CompanyRepository;
import com.example.company_directory.repository.ZipMasterRepository;
import com.example.company_directory.util.ExcelHelper;

@Service
public class ExcelImportService {

    private final CompanyRepository companyRepository;
    private ZipMasterRepository zipMasterRepository;

    // コンストラクタインジェクション
    public ExcelImportService(CompanyRepository companyRepository, ZipMasterRepository zipMasterRepository) {
        this.companyRepository = companyRepository;
        this.zipMasterRepository = zipMasterRepository;
    }

    //Excel検証結果を返す
    //ファイル構造チェック→データエラーチェック→データ警告チェック
    public ImportResultDto importExcel(InputStream inputStream) {

        List<ImportRowDto> rows = ExcelHelper.parseExcel(inputStream);
    
        ImportResultDto result = new ImportResultDto();
    
        // --- Excel 内の同名企業 / 同住所チェック用 ---
        Map<String, List<Integer>> nameToRows = new HashMap<>();
        Map<String, List<Integer>> addressToRows = new HashMap<>();

        // まず全件スキャンしてカウント
        for (ImportRowDto r : rows) {
            String name = normalizeString(r.getCompanyName());
            String addr = normalizeString(r.getAddress());

            if (name != null && !name.isEmpty()) {
                nameToRows.computeIfAbsent(name, k -> new ArrayList<>()).add(r.getRowNum());
            }
            if (addr != null && !addr.isEmpty()) {
                addressToRows.computeIfAbsent(addr, k -> new ArrayList<>()).add(r.getRowNum());
            }
        }

        Set<String> excelCompanyIdSet = new HashSet<>();
        Set<String> dbCompanyIdSet = companyRepository.findAllIds();

        int success = 0;
        int worning = 0;
        int error = 0;
    
        for (ImportRowDto row : rows) {
            List<String> errs = validateRow(row, excelCompanyIdSet, dbCompanyIdSet);

            result.getTotalList().add(row);

            if (errs.isEmpty()) {

                // 3. ★追加: 新規・更新の判定
                if (row.getCompanyId() != null && !row.getCompanyId().isBlank()) {
                    try {
                        Integer id = Integer.parseInt(row.getCompanyId());
                        // DBにIDが存在すれば「更新」、なければ「新規(ID指定)」
                        boolean exists = companyRepository.existsById(id);
                        row.setUpdate(exists);
                    } catch (NumberFormatException e) {
                        // IDが数値でない場合はバリデーションで弾かれているはずだが念のため
                        row.setUpdate(false); 
                    }
                } else {
                    // IDなしなら「新規(自動採番)」
                    row.setUpdate(false);
                }

                row.setValid(true);

                List<String> warns = checkWarning(row, nameToRows, addressToRows);

                if (warns.isEmpty()){
                    result.getSuccessList().add(row);
                    success++;
                } else {
                    row.setWarningMessages(warns);
                    row.setHasWarning(true);
                    result.getWarningList().add(new ImportWorningDto(row.getRowNum(), warns));
                    worning++;
                }
            } else {
                row.setErrorMessages(errs);
                row.setHasError(true);
                row.setValid(false);
                result.getErrorList().add(new ImportErrorDto(row.getRowNum(), errs));
                error++;
            }
        }
    
        result.setTotalCount(rows.size());
        result.setSuccessCount(success);
        result.setWarningCount(worning);
        result.setErrorCount(error);
    
        return result;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    //行データのエラーチェック
    private List<String> validateRow(ImportRowDto row, Set<String> excelCompanyIdSet, Set<String> dbCompanyIdSet) {
        List<String> errors = new ArrayList<>();

        // -------- 1. 必須チェック --------
        if (isBlank(row.getCompanyName())) {
            errors.add("エラー：必須項目が空欄です：企業名を入力してください。");
        }
        if (isBlank(row.getZipCode())) {
            errors.add("エラー：必須項目が空欄です：郵便番号を入力してください。");
        }
        if (isBlank(row.getAddress())) {
            errors.add("エラー：必須項目が空欄です：住所を入力してください。");
        }

        // -------- 2. 企業ID 形式チェック --------
        if (row.getCompanyId() != null && !isBlank(row.getCompanyId())) {
            // 半角数字のみ
            if (!row.getCompanyId().matches("\\d+")) {
                errors.add("エラー：企業IDが正しい形式ではありません（半角数字のみ）。");
            }

            // Excel内で重複しているか？
            if (excelCompanyIdSet.contains(row.getCompanyId())) {
                errors.add("エラー：同一ファイル内で企業IDが重複しています。");
            } else {
                excelCompanyIdSet.add(row.getCompanyId());
            }

            // DB上に存在しないID（更新モードの場合）
            if (!dbCompanyIdSet.contains(row.getCompanyId())) {
                errors.add("エラー：企業IDが未登録のため更新できません。");
            }

            try {
                // 数値変換できるか、Integerの最大値を超えていないかチェック
                int id = Integer.parseInt(row.getCompanyId());

                // (オプション) IDは1以上であるべき
                if (id < 1) {
                    errors.add("エラー：企業IDは1以上の数値を指定してください。");
                }
            } catch (NumberFormatException e) {
                errors.add("エラー：企業IDは数値（整数）で入力してください。");
            }
        }

        // -------- 3. 郵便番号形式 --------
        if (!isBlank(row.getZipCode())) {
            if (!row.getZipCode().matches("\\d{3}-\\d{4}")) {
                errors.add("エラー：郵便番号の形式が不正です（例：123-4567）。");
            }
        }

        // -------- 5. 日付形式チェック --------
        if (!isBlank(row.getRegistrationDate())) {
            if (!isValidDate(row.getRegistrationDate())) {
                errors.add("エラー：データ形式が不正です：日付として認識できません。");
            }
        }


        // -------- 6. 文字数オーバー --------
        //企業名
        if (!isBlank(row.getCompanyName()) && row.getCompanyName().length() > 100) {
            errors.add("エラー：企業名の文字数が上限（100文字）を超えています。");
        }

        //住所
        if (!isBlank(row.getAddress()) && row.getAddress().length() > 200) {
            errors.add("エラー：住所の文字数が上限（200文字）を超えています。");
        }

        //郵便番号
        if (!isBlank(row.getZipCode()) && row.getZipCode().length() > 8) {
            errors.add("エラー：郵便番号の文字数が上限（8文字）を超えています。");
        }

        //備考
        if (!isBlank(row.getRemarks()) && row.getRemarks().length() > 1000) {
            errors.add("エラー：項目の文字数が上限を超えています（備考は1000文字まで）。");
        }

        return errors;
    }

    private boolean isValidDate(String dateStr) {
        return parseFlexibleDate(dateStr) != null;
    }

    private LocalDate parseFlexibleDate(String text) {
        if (isBlank(text)) return null;
        String t = text.trim();
        // try some common patterns in order:
        String[] patterns = {
            "yyyy-MM-dd", "yyyy/MM/dd", "yyyy-M-d", "yyyy/M/d", "yyyy年MM月dd日",
            "M/d/yy", "MM/dd/yy", "M/d/yyyy", "MM/dd/yyyy"
        };
        for (String p : patterns) {
            try {
                DateTimeFormatter f = DateTimeFormatter.ofPattern(p);
                return LocalDate.parse(t, f);
            } catch (DateTimeParseException ex) {
                // try next
            }
        }
        // If it's an ISO-like already, try LocalDate.parse
        try {
            return LocalDate.parse(t);
        } catch (Exception e) {
            return null;
        }
    }

    //行データの警告チェック
    private List<String> checkWarning(ImportRowDto row, Map<String, List<Integer>> excelNameToRows, Map<String, List<Integer>> excelAddressToRows) {
        List<String> warnings = new ArrayList<>();

        String name = normalizeString(row.getCompanyName());
        String addr = normalizeString(row.getAddress());

        //正式な企業名かと存在する企業名なのかチェックをする処理を後で追加

        // 1) Excel内：同名企業（自分以外の行がある場合）
        if (name != null && !name.isEmpty()) {
            List<Integer> lines = excelNameToRows.getOrDefault(name, Collections.emptyList());
            // 自分の行をのぞいたリストを作る
            List<Integer> otherLines = lines.stream()
                    .filter(rn -> rn != row.getRowNum())
                    .collect(Collectors.toList());
            if (!otherLines.isEmpty()) {
                warnings.add("警告：" + buildDuplicateMessage("アップロードデータ内で同名の企業が複数あります。", otherLines));
            }
        }

        // 2) Excel内：同住所（自分以外）
        if (addr != null && !addr.isEmpty()) {
            List<Integer> lines = excelAddressToRows.getOrDefault(addr, Collections.emptyList());
            List<Integer> otherLines = lines.stream()
                    .filter(rn -> rn != row.getRowNum())
                    .collect(Collectors.toList());
            if (!otherLines.isEmpty()) {
                warnings.add("警告：" + buildDuplicateMessage("アップロードデータ内で同じ住所が複数あります。", otherLines));
            }
        }
    
        // 3) DBとのチェック（既存同名）
        if ((row.getCompanyId() == null || row.getCompanyId().isEmpty())
                && name != null && !name.isEmpty()) {
            if (companyRepository.existsByCompanyName(row.getCompanyName())) {
                warnings.add("警告：同名の企業が既に登録されています。");
            }
        }

        // 4) 新規かつ DB で住所一致
        if ((row.getCompanyId() == null || row.getCompanyId().isEmpty())
                && addr != null && !addr.isEmpty()) {
            if (companyRepository.existsByAddress(row.getAddress())) {
                warnings.add("警告：既存企業と住所が一致しています。登録済みデータの可能性があります。");
            }
        }
    
        // 5) 登録日が未来日か（文字列もしくは既に ISO 日付文字列）
        if (!isBlank(row.getRegistrationDate())) {
            LocalDate d = parseFlexibleDate(row.getRegistrationDate());
            if (d != null && d.isAfter(LocalDate.now())) {
                warnings.add("警告：登録日が未来の日付になっています。");
            }
        }
        
        // 6) 住所が極端に短い
        if (!isBlank(row.getAddress()) && row.getAddress().length() <= 5) {
            warnings.add("警告：住所が極端に短いため、番地などの入力漏れの可能性があります。");
        }

        // 7) 郵便番号と住所の不整合
        checkAddressConsistency(row, warnings);

        return warnings;
    }

    private String normalizeString(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String buildDuplicateMessage(String base, List<Integer> lines) {
        String joined = lines.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        return base + "（" + joined + "行目）";
    }   

    // 住所チェックロジック
    private void checkAddressConsistency(ImportRowDto row, List<String> warnings) {
        String inputZip = row.getZipCode();
        String inputAddress = row.getAddress();

        if (isBlank(inputZip) || isBlank(inputAddress)) return;

        // 1. 郵便番号の正規化（ハイフン除去）
        String cleanZip = inputZip.replace("-", "");

        // 2. マスタ検索
        ZipMaster master = zipMasterRepository.findByZipCode(cleanZip);
        
        // マスタにない郵便番号の場合はスキップ（または「郵便番号が存在しません」と警告）
        if (master == null) {
            warnings.add("警告：郵便番号が存在しません ");
            return; 
        }

        // --- ① 市区町村まで ---
        String expectedBase = master.getPrefecture() + master.getCity();
        if (!inputAddress.startsWith(expectedBase)) {
            warnings.add(String.format(
                "警告：住所と郵便番号が一致しない可能性があります（郵便番号 %s は %s です）。",
                inputZip, expectedBase
            ));
            return; // 市区町村ズレは強めなのでここで止める
        }

        // 4. 町名チェック（町名が書かれている場合のみ）
    String masterTown = master.getTown();
    if (!isBlank(masterTown)) {

        // 入力住所に「町名っぽい情報」が含まれているか
        boolean townWritten =
                inputAddress.contains("町")
             || inputAddress.contains("丁目")
             || inputAddress.contains(masterTown);

        // 町名が書かれているのに、マスタ町名と一致しない場合だけ警告
        if (townWritten && !inputAddress.contains(masterTown)) {
            warnings.add("警告：町名が郵便番号の情報と一致していません。");
        }
    }

        //丁目と番地チェックを余裕があれば追加
    }
    
     /**
     * ★追加: 確定登録処理
     * 有効なデータだけをDBに保存する
     */
    @Transactional // トランザクション制御（途中で失敗したら全部ロールバック）
    public void saveValidData(List<ImportRowDto> successList) {

        // 日付フォーマットの定義 (Excelの入力形式に合わせる)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");

        for (ImportRowDto row : successList) {
            Company company;

            // IDがある場合は「更新」、ない場合は「新規」
            if (row.getCompanyId() != null && !row.getCompanyId().isBlank()) {
                Integer id = Integer.parseInt(row.getCompanyId());
                company = companyRepository.findById(id).orElse(null);
                if (company == null) continue;
            } else {
                company = new Company();
                company.setIsDeleted(false);
            }

            // DTO -> Entity 詰め替え
            company.setCompanyName(row.getCompanyName());
            company.setAddress(row.getAddress());
            company.setZipCode(row.getZipCode());
            company.setRemarks(row.getRemarks());

            if (StringUtils.hasText(row.getRegistrationDate())) {
                try {
                    // Excelに値がある場合
                    LocalDate date = LocalDate.parse(row.getRegistrationDate(), formatter);
                    company.setRegistrationDate(date);
                } catch (DateTimeParseException e) {
                    company.setRegistrationDate(LocalDate.now());
                }
            } else {
                // Excelが空欄の場合
                if (company.getRegistrationDate() == null) {
                    // 新規登録なら「今日」を入れる
                    company.setRegistrationDate(LocalDate.now());
                }
            }
            
            companyRepository.save(company);
        }
    }

}
