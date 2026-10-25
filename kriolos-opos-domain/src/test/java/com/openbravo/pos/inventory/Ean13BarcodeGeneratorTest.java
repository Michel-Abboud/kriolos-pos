package com.openbravo.pos.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Ean13BarcodeGeneratorTest {

    @Test
    void appendsKnownCheckDigit() {
        assertEquals("4006381333931", Ean13BarcodeGenerator.appendCheckDigit("400638133393"));
    }

    @Test
    void generatedValuesAreInternalUseAndValid() {
        for (int count = 0; count < 100; count++) {
            String barcode = Ean13BarcodeGenerator.generate();
            assertTrue(barcode.startsWith("200"));
            assertTrue(Ean13BarcodeGenerator.isValid(barcode));
        }
    }

    @Test
    void rejectsWrongLengthCharactersAndChecksum() {
        assertThrows(IllegalArgumentException.class,
                () -> Ean13BarcodeGenerator.appendCheckDigit("123"));
        assertThrows(IllegalArgumentException.class,
                () -> Ean13BarcodeGenerator.appendCheckDigit("40063813339A"));
        assertFalse(Ean13BarcodeGenerator.isValid("4006381333932"));
    }
}
