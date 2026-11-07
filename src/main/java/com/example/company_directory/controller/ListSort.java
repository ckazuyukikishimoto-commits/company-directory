package com.example.company_directory.controller;

import java.util.Set;

import org.springframework.data.domain.Sort;

/**
 * 一覧・ゴミ箱の並び替えの条件を、画面から送られた sort パラメータから決める。
 *
 * <p>並び替えはサーバー側（DB）で行い、ページを移っても順番が保たれるようにする。
 * 画面に無い列や存在しない列名が URL に書かれても 500 エラーにならないよう、
 * 決まった列だけを受け付け、それ以外は画面ごとの既定の並びに戻す
 * （一覧は No. の昇順、ゴミ箱は削除日時の新しい順）。</p>
 */
final class ListSort {

    /** 企業一覧で並び替えに使える列（list.html の見出しの data-col と同じ） */
    static final Set<String> LIST_COLUMNS = Set.of(
            "displayNumber", "companyId", "companyName", "address", "zipCode", "registrationDate", "remarks");

    /** ゴミ箱で並び替えに使える列（trash.html の見出しの data-col と同じ） */
    static final Set<String> TRASH_COLUMNS = Set.of(
            "displayNumber", "companyId", "companyName", "zipCode", "address", "deletedAt", "deletedBy");

    /** 企業一覧の既定の並び: No. の昇順 */
    static final Sort.Order LIST_DEFAULT = Sort.Order.asc("displayNumber");

    /** ゴミ箱の既定の並び: 削除日時の新しい順（削除履歴画面仕様書のとおり。最近消したものを探しやすい） */
    static final Sort.Order TRASH_DEFAULT = Sort.Order.desc("deletedAt");

    private static final String TIE_BREAKER = "companyId";

    private ListSort() {
    }

    /**
     * 送られた並び替えのうち、使える列の最初の1つだけを採用する。
     * 同じ値の行（住所が同じ・空の備考など）の順番がページをまたいで入れ替わらないよう、
     * 2つ目の並びとして企業ID の昇順を足す。
     */
    static Sort resolve(Sort requested, Set<String> allowed, Sort.Order defaultOrder) {
        Sort.Order order = requested.stream()
                .filter(o -> allowed.contains(o.getProperty()))
                .findFirst()
                .orElse(defaultOrder);
        Sort sort = Sort.by(order.getDirection(), order.getProperty());
        if (!TIE_BREAKER.equals(order.getProperty())) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, TIE_BREAKER));
        }
        return sort;
    }

    /** 画面のリンク・フォームに引き継ぐ sort パラメータの値（例: "companyName,desc"） */
    static String toParam(Sort sort) {
        return toParam(sort.iterator().next());
    }

    /** 1つの並びを sort パラメータの形にする（画面の「既定の並び」を渡すときにも使う） */
    static String toParam(Sort.Order order) {
        return order.getProperty() + "," + order.getDirection().name().toLowerCase();
    }
}
