package mx.edu.utez.sij.core.util;

import java.security.SecureRandom;
import java.time.LocalDate;

public class EmployeeCodeUtil {
    private static final SecureRandom random = new SecureRandom();

    public static String generateEmployeeCode(String rol, String nombreCompleto, int idConsecutivo) {
        String prefix = "SIJ";
        if (rol != null) {
            if (rol.contains("OFICIALIA")) prefix = "OFI";
            else if (rol.contains("AUDIENCIA")) prefix = "AUD";
            else if (rol.contains("TOCA") || rol.contains("MAGISTRADO")) prefix = "TOC";
            else if (rol.contains("PERITO")) prefix = "PER";
        }

        StringBuilder initials = new StringBuilder();
        if (nombreCompleto != null && !nombreCompleto.isBlank()) {
            for (String part : nombreCompleto.trim().split("\\s+")) {
                if (!part.isEmpty()) {
                    initials.append(Character.toUpperCase(part.charAt(0)));
                }
            }
        }
        String initStr = initials.length() > 0 ? initials.toString() : "EMP";
        int yearTwoDigits = LocalDate.now().getYear() % 100;

        return String.format("%s-%s-%02d-%04d", prefix, initStr, yearTwoDigits, idConsecutivo);
    }

    public static String generateSixDigitCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}