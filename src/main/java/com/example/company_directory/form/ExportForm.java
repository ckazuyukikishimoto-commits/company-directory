package com.example.company_directory.form;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExportForm extends CompanySearchForm {
    private String scope; // "ALL", "SEARCH", "SELECTION"
    private List<Integer> selectedIds;
    private List<String> columns;
    private String columnOrder;
    private String fileName;

    public List<String> resolveOrderedColumns() {
        List<String> selected = this.columns != null ? new ArrayList<>(this.columns) : new ArrayList<>();
        if (columnOrder == null || columnOrder.isBlank()) {
            return selected;
        }

        List<String> ordered = new ArrayList<>();
        for (String key : columnOrder.split(",")) {
            String trimmed = key.trim();
            if (!trimmed.isEmpty() && selected.contains(trimmed) && !ordered.contains(trimmed)) {
                ordered.add(trimmed);
            }
        }

        for (String key : selected) {
            if (!ordered.contains(key)) {
                ordered.add(key);
            }
        }
        return ordered;
    }

    // Sort options
    private String sortBy = "companyId"; // Default
    private String sortOrder = "ASC"; // Default
}
