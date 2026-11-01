package com.openbravo.pos.catalog;

import com.openbravo.pos.pim.CategoryInfo;
import com.openbravo.pos.sales.TaxService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogVisibilityTest {
    @Test
    void legacyUnsetCatalogFlagsRemainVisibleAndExplicitlyDisabledCategoriesStayHidden() throws Exception {
        CategoryInfo legacy = category("legacy", null);
        CategoryInfo enabled = category("enabled", true);
        CategoryInfo disabled = category("disabled", false);
        CatalogService catalog = mock(CatalogService.class);
        TaxService tax = mock(TaxService.class);
        when(tax.getTaxListAll()).thenReturn(List.of());
        when(catalog.getRootCategories()).thenReturn(List.of(legacy, enabled, disabled));
        when(catalog.getSubcategories("parent")).thenReturn(List.of(legacy, enabled, disabled));
        CatalogController controller = new CatalogController(tax, catalog);
        assertEquals(List.of(legacy, enabled), controller.getRootCategories());
        assertEquals(List.of(legacy, enabled), controller.getSubcategories("parent"));
    }

    private static CategoryInfo category(String id, Boolean enabled) {
        CategoryInfo category = new CategoryInfo(id, id, null, "", true);
        category.setCatalogEnabled(enabled);
        return category;
    }
}
