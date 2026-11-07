package com.example.company_directory.controller;

import java.util.Set;

import org.springframework.data.domain.Sort;

/**
 * 一覧・ゴミ箱の並び替えの条件を、画面から送られた sort パラメータから決める。
 *
 * <p>並び替えはサーバー側（DB）で行い、ページを移っても順番が保たれるようにする。
 * 画面に無い列や存在しない列名が URL に書かれても 500 エラーにならないよう、
 * 決まった列だけを受け付け、それ以外は既定（No. の昇順）に戻す。</p>
 */
final class ListSort {

    /** 企業一覧で並び替えに使える列（list.html の見出しの data-col と同じ） */
    static final Set<String> LIST_COLUMNS = Set.of(
            "displayNumber", "companyId", "companyName", "address", "zipCode", "registrationDate", "remarks");

    /** ゴミ箱で並び替えに使える列（trash.html の見出しの data-col と同じ） */
    static final Set<String> TRASH_COLUMNS = Set.of(
            "displayNumber", "companyId", "companyName", "zipCode", "address", "deletedAt", "deletedBy");

    private static final String DEFAULT_COLUMN = "displayNumber";
    private static final String TIE_BREAKER = "companyId";

    private ListSort() {
    }

    /**
     * 送られた並び替えのうち、使える列の最初の1つだけを採用する。
     * 同じ値の行（住所が同じ・空の備考など）の順番がページをまたいで入れ替わらないよう、
     * 2つ目の並びとして企業ID の昇順を足す。
     */
    static Sort resolve(Sort requested, Set<String> allowed) {
        Sort.Order order = requested.stream()
                .filter(o -> allowed.contains(o.getProperty()))
                .findFirst()
                .orElse(Sort.Order.asc(DEFAULT_COLUMN));
        Sort sort = Sort.by(order.getDirection(), order.getProperty());
        if (!TIE_BREAKER.equals(order.getProperty())) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, TIE_BREAKER));
        }
        return sort;
    }

    /** 画面のリンク・フォームに引き継ぐ sort パラメータの値（例: "companyName,desc"） */
    static String toParam(Sort sort) {
        Sort.Order first = sort.iterator().next();
        return first.getProperty() + "," + first.getDirection().name().toLowerCase();
    }
}
