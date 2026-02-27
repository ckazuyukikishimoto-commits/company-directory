package com.example.company_directory.repository;

import com.example.company_directory.entity.Company;
import com.example.company_directory.form.CompanySearchForm;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
                return spec.toPredicate(root, query, cb);
            }

            // 1. キーワード検索（企業名 OR 住所 OR 郵便番号 OR 備考 OR 企業ID）
            if (StringUtils.hasText(form.getKeyword())) {
                String pattern = "%" + form.getKeyword() + "%";
                Specification<Company> keywordSpec = (r, q, c) -> {
                    // PostgreSQLでは整数カラムに直接LIKEを適用できないため、
                    // companyIdは空文字との連結で文字列化してからLIKE検索する。
                    // （生成SQLイメージ: concat('', company_id) like ?）
                    var companyIdAsText = c.concat("", r.get("companyId").as(String.class));
                    var displayNumberAsText = c.concat("", r.get("displayNumber").as(String.class));

                    List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                    predicates.add(c.like(companyIdAsText, pattern));
                    predicates.add(c.like(r.get("companyName"), pattern));
                    predicates.add(c.like(r.get("address"), pattern));
                    predicates.add(c.like(r.get("zipCode"), pattern));
                    predicates.add(c.like(r.get("remarks"), pattern));
                    predicates.add(c.like(displayNumberAsText, pattern));

                    if (isDeleted) {
                        predicates.add(c.like(r.get("deletedBy"), pattern));
                        var deletedAtText = c.function("to_char", String.class, r.get("deletedAt"),
                                c.literal("YYYY-MM-DD HH24:MI:SS"));
                        predicates.add(c.like(deletedAtText, pattern));
                    }

                    return c.or(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                };
                spec = spec.and(keywordSpec);
            }

            // 2. 詳細検索 - 企業ID (完全一致)
            if (form.getNo() != null) {
                spec = spec.and((r, q, c) -> c.equal(r.get("displayNumber"), form.getNo()));
            }

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

            // 7. 削除日時・削除実行者（削除済みのみ）
            if (isDeleted) {
                if (StringUtils.hasText(form.getDeletedBy())) {
                    spec = spec.and((r, q, c) -> c.like(r.get("deletedBy"), "%" + form.getDeletedBy() + "%"));
                }

                if (form.getDeletedFrom() != null) {
                    LocalDateTime from = form.getDeletedFrom().atStartOfDay();
                    spec = spec.and((r, q, c) -> c.greaterThanOrEqualTo(r.get("deletedAt"), from));
                }
                if (form.getDeletedTo() != null) {
                    LocalDateTime to = form.getDeletedTo().plusDays(1).atStartOfDay();
                    spec = spec.and((r, q, c) -> c.lessThan(r.get("deletedAt"), to));
                }
            }

            return spec.toPredicate(root, query, cb);
        };
    }
}
