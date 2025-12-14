package com.example.company_directory.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.company_directory.dto.ImportResultDto;
import com.example.company_directory.dto.ImportRowDto;
import com.example.company_directory.service.ExcelImportService;
import com.example.company_directory.util.ExcelHelper;

@Controller
@RequestMapping("/companies")
public class ImportController {

    private final ExcelImportService excelImportService;

    public ImportController(ExcelImportService excelImportService){
        this.excelImportService=excelImportService;
    }
    
    @GetMapping("/import")
    public String upload(@RequestParam(name = "error", required = false) String error, Model model) {
        if ("session".equals(error)) {
            model.addAttribute("errorMessage", "セッションが切れました。再度ファイルをアップロードしてください。");
        }
        return "companies/import";
    }

    @PostMapping("/import")
    public String importExcel(@RequestParam("file") MultipartFile file, Model model) {
        try {
            //ファイルが空の場合
            if (file == null || file.isEmpty()) {
                model.addAttribute("errorMessage", "ファイルが選択されていません。");
                return "companies/import";
            }
            //ファイル名、拡張子が正しくない場合
            String fileName = file.getOriginalFilename();
            if (fileName == null || !fileName.endsWith(".xlsx")) {
                model.addAttribute("errorMessage", "Excelファイル(.xlsx)のみアップロード可能です。");
                return "companies/import";
            }

            // ファイルを一時フォルダに保存する
            // ファイル名が被らないようにUUIDを使う
            String tempFileName = UUID.randomUUID().toString() + ".xlsx";
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir")); // OSの一時フォルダ
            Path tempFilePath = tempDir.resolve(tempFileName);
            
            // ファイルをコピー配置
            Files.copy(file.getInputStream(), tempFilePath, StandardCopyOption.REPLACE_EXISTING);

            // Excel を解析して結果を model に詰める（一時ファイルから読み込む）
            try (InputStream inputStream = Files.newInputStream(tempFilePath)) {
                ImportResultDto result = excelImportService.importExcel(inputStream);
                model.addAttribute("importResult", result);
            }
            model.addAttribute("tempFileName", tempFileName);

            // 同じ画面に戻してプレビューを表示
            return "companies/import";
        } catch (IOException e) {
            model.addAttribute("errorMessage", "アップロードしたファイルの読み込みに失敗しました。");
            return "companies/import";
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "companies/import";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "予期しないエラーが発生しました。");
            return "companies/import";
        }
    }

    // ★追加: 確定登録実行
    @PostMapping("/import/execute")
    public String executeImport(@RequestParam("tempFileName") String tempFileName, @RequestParam(name = "mode", defaultValue = "all") String mode, Model model, RedirectAttributes redirectAttributes) {
        ImportResultDto result = null;
        try {
            // 1. 一時ファイルのパスを取得
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
            Path tempFilePath = tempDir.resolve(tempFileName);

            // ファイルが存在するかチェック
            if (!Files.exists(tempFilePath)) {
                return "redirect:/companies/import?error=session";
            }

            // 2. 再度解析して、成功データだけを取り出す
            // (メモリ節約のため、ここで再解析するのが安全です)
            try (InputStream is = Files.newInputStream(tempFilePath)) {
                result = excelImportService.importExcel(is);
            }
            

            // 2. 登録対象の選定
            List<ImportRowDto> targetList;

            if ("strict".equals(mode)) {
                // 正常データのみ（警告なし）
                targetList = result.getSuccessList();
            } else {
                // 正常 + 警告
                targetList =result.getTotalList().stream()
                            .filter(row -> !row.isHasError()) // エラー除外
                            .toList();
            }


            // 3. DB保存
            if (targetList.isEmpty()) {
                model.addAttribute("errorMessage", "登録対象のデータがありません。");
                model.addAttribute("importResult", result);
                model.addAttribute("tempFileName", tempFileName);
                return "companies/import"; // 画面に戻る
            }
            
            excelImportService.saveValidData(targetList);

            // 4. 後始末（一時ファイルを削除）
            Files.deleteIfExists(tempFilePath);

            //ここはモーダルウィンドウで結果表示をする
            // 5. 完了画面（または一覧）へリダイレクト
            redirectAttributes.addFlashAttribute("message", targetList.size() + "件のデータを登録しました。");
            return "redirect:/companies";

            //後でエラー処理を追加
        } catch (IOException e) {
            model.addAttribute("errorMessage", "ファイルの読み込みに失敗しました。");
        
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("errorMessage",
                "データの重複が検出されました。既に登録されているデータがあります。"
            );
        
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage",
                e.getMessage() != null ? e.getMessage() : "データの解析に失敗しました。"
            );
        
        } catch (Exception e) {
            model.addAttribute("errorMessage", "予期しないエラーが発生しました。");
        }
        
        // 共通の再表示処理
        if (result != null) {
            model.addAttribute("importResult", result);
            model.addAttribute("tempFileName", tempFileName);
        }
        return "companies/import";
    }

    @PostMapping("/import/download")
    public ResponseEntity<InputStreamResource> downloadRows(
            @RequestParam("tempFileName") String tempFileName,
            @RequestParam("target") String target) throws IOException {

        // 1. 一時ファイル読み込み＆解析
        Path tempFilePath = Paths.get(System.getProperty("java.io.tmpdir"), tempFileName);
        InputStream is = Files.newInputStream(tempFilePath);
        ImportResultDto result = excelImportService.importExcel(is);

        // 2. ダウンロード対象のフィルタリング
        List<ImportRowDto> exportList = new ArrayList<>();

        if ("error".equals(target) || "both".equals(target)) {
            // エラー分を追加
            exportList.addAll(result.getTotalList().stream()
                    .filter(row -> row.getErrorMessages() != null && !row.getErrorMessages().isEmpty())
                    .toList());
        }
        
        if ("warning".equals(target) || "both".equals(target)) {
            // 警告分を追加
            exportList.addAll(result.getTotalList().stream()
                    .filter(ImportRowDto::isHasWarning)
                    .toList());
        }

        // 3. Excel生成 (ExcelHelperに追加が必要)
        ByteArrayInputStream stream = ExcelHelper.dtosToExcel(exportList);

        // 4. ファイル名決定
        String filename = "import_" + target + "_" + LocalDate.now() + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(stream));
    }
}
