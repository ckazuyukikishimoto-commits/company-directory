package com.example.company_directory.repository;

import com.example.company_directory.entity.Company;
import com.example.company_directory.form.CompanySearchForm;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class CompanySpecification {

    public static Specification<Company> search(CompanySearchForm form) {
        return search(form, false);
    }

    public static Specification<Company> search(CompanySearchForm form, boolean isDeleted) {
        return (root, query, cb) -> {
            // ベース条件: 指定された削除フラグと一致すること
            Specification<Company> spec = (rootSpec, querySpec, cbSpec) -> cbSpec.equal(rootSpec.get("isDeleted"),
                    isDeleted);

            if (form == null) {
                // ソート順のみ設定して返す
                if (isDeleted) {
                    query.orderBy(cb.desc(root.get("deletedAt")));
                } else {
                    query.orderBy(cb.asc(root.get("companyId")));
                }
                return spec.toPredicate(root, query, cb);
            }

            // 1. キーワード検索（企業名 OR 住所 OR 郵便番号 OR 備考 OR 企業ID）
            if (StringUtils.hasText(form.getKeyword())) {
                String pattern = "%" + form.getKeyword() + "%";
                Specification<Company> keywordSpec = (r, q, c) -> c.or(
                        c.like(r.get("companyId").as(String.class), pattern),
                        c.like(r.get("companyName"), pattern),
                        c.like(r.get("address"), pattern),
                        c.like(r.get("zipCode"), pattern),
                        c.like(r.get("remarks"), pattern));
                spec = spec.and(keywordSpec);
            }

            // 2. 詳細検索 - 企業ID (完全一致)
            if (form.getCompanyId() != null) {
                spec = spec.and((r, q, c) -> c.equal(r.get("companyId"), form.getCompanyId()));
            }

            // 3. 詳細検索 - 企業名 (部分一致)
            if (StringUtils.hasText(form.getCompanyName())) {
                spec = spec.and((r, q, c) -> c.like(r.get("companyName"), "%" + form.getCompanyName() + "%"));
            }

            // 4. 詳細検索 - 住所 (部分一致)
            if (StringUtils.hasText(form.getAddress())) {
                spec = spec.and((r, q, c) -> c.like(r.get("address"), "%" + form.getAddress() + "%"));
            }
            // 5. 詳細検索 - 郵便番号 (部分一致)
            if (StringUtils.hasText(form.getZipCode())) {
                spec = spec.and((r, q, c) -> c.like(r.get("zipCode"), "%" + form.getZipCode() + "%"));
            }
            // 6. 登録日 (範囲検索)
            if (form.getDateFrom() != null) {
                spec = spec.and((r, q, c) -> c.greaterThanOrEqualTo(r.get("registrationDate"), form.getDateFrom()));
            }
            if (form.getDateTo() != null) {
                spec = spec.and((r, q, c) -> c.lessThanOrEqualTo(r.get("registrationDate"), form.getDateTo()));
            }

            // 最終的なクエリを生成
            if (isDeleted) {
                query.orderBy(cb.desc(root.get("deletedAt")));
            } else {
                query.orderBy(cb.asc(root.get("companyId")));
            }

            return spec.toPredicate(root, query, cb);
        };
    }
}
