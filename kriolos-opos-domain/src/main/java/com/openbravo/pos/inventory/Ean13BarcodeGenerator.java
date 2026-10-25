package com.openbravo.pos.inventory;

import java.security.SecureRandom;

/** Generates checksum-valid EAN-13 values for internal product identification. */
public final class Ean13BarcodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INTERNAL_PREFIX = "200";

    private Ean13BarcodeGenerator() {
    }

    /** Generates a 13-digit barcode using the internal-use 200 prefix. */
    public static String generate() {
        StringBuilder payload = new StringBuilder(INTERNAL_PREFIX);
        while (payload.length() < 12) {
            payload.append(RANDOM.nextInt(10));
        }
        return appendCheckDigit(payload.toString());
    }

    /** Appends the EAN-13 check digit to a 12-digit payload. */
    public static String appendCheckDigit(String payload) {
        if (payload == null || !payload.matches("\\d{12}")) {
            throw new IllegalArgumentException("EAN-13 payload must contain exactly 12 digits");
        }
        int sum = 0;
        for (int index = 0; index < payload.length(); index++) {
            int digit = payload.charAt(index) - '0';
            sum += index % 2 == 0 ? digit : digit * 3;
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return payload + checkDigit;
    }

    /** Returns whether the value has 13 digits and a correct EAN-13 checksum. */
    public static boolean isValid(String barcode) {
        return barcode != null && barcode.matches("\\d{13}")
                && appendCheckDigit(barcode.substring(0, 12)).equals(barcode);
    }
}
