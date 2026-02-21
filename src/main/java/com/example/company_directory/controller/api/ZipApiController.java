package com.example.company_directory.controller.api;

import com.example.company_directory.entity.ZipMaster;
import com.example.company_directory.repository.ZipMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController // HTMLではなくJSONを返すコントローラー
@RequestMapping("/api/zip")
@RequiredArgsConstructor
public class ZipApiController {

    private final ZipMasterRepository zipMasterRepository;

    /**
     * 郵便番号から住所を取得
     * GET /api/zip/1000005
     */
    @GetMapping("/{zipCode}")
    public ResponseEntity<?> getAddress(@PathVariable String zipCode) {
        // ハイフン除去
        String cleanZip = zipCode.replace("-", "");

        if (!cleanZip.matches("\\d{7}")) {
            return ResponseEntity.badRequest().body(Map.of("message", "郵便番号は7桁の数字で入力してください。"));
        }

        ZipMaster master = zipMasterRepository.findByZipCode(cleanZip);

        if (master != null) {
            return ResponseEntity.ok(master);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 住所から郵便番号を検索
     * GET /api/zip/search?keyword=丸の内
     */
    @GetMapping("/search")
    public ResponseEntity<List<ZipMaster>> searchAddress(@RequestParam("keyword") String keyword) {
        List<ZipMaster> list = zipMasterRepository.searchByAddress(keyword);
        return ResponseEntity.ok(list); // 件数制限（limit）を入れるとより安全です
    }
}
