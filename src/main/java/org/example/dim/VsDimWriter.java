package org.example.dim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Escribe la VS DIM de vuelta al Vital Bracelet con los Vital Values
 * nuevos. Parte SIEMPRE de los bytes de la VS DIM original y cambia solo
 * dos cosas, igual que hizo el propio VB en "VS DIM MagnaKidmon, Battle
 * step.bin" (4 victorias: solo cambiaron los Vital Values y el checksum):
 *
 *   0x40000 + 2   Vital Values (u16 LE, con NOT como todo el archivo)
 *   0x120000      checksum u16 LE = suma de 16 bits de todos los u16 (ya
 *                 sin el NOT) de [0x10000, 0x120000), guardado con NOT.
 *                 Confirmado en 5 archivos reales del VB.
 *
 * La VS DIM no tiene un campo conocido de "ganó/perdió": el resultado viaja
 * como Vital Values (decisión de diseño).
 */
public final class VsDimWriter {

    private static final int FILE_SIZE = 4 * 1024 * 1024;
    private static final int VITAL_VALUES_OFFSET = 0x40000 + 2;
    private static final int CHECKSUM_FROM = 0x10000;
    private static final int CHECKSUM_OFFSET = 0x120000;

    private VsDimWriter() {}

    /** Copia de {@code source} en {@code target} con los Vital Values cambiados y el checksum recalculado. */
    public static void writeWithVitalValues(Path source, Path target, int vitalValues) throws IOException {
        if (vitalValues < 0 || vitalValues > 0xFFFF) throw new IOException("Vital Values fuera de rango: " + vitalValues);
        byte[] raw = Files.readAllBytes(source);
        if (raw.length != FILE_SIZE) throw new IOException(source.getFileName() + " no mide 4 MB.");
        if (!storedChecksumMatches(raw)) {
            // Un archivo que no salió tal cual del VB: mejor no fabricar uno "válido" a partir de él.
            throw new IOException(source.getFileName() + " tiene el checksum inválido (¿fue editado?). No se devolverá.");
        }
        putNot16(raw, VITAL_VALUES_OFFSET, vitalValues);
        putNot16(raw, CHECKSUM_OFFSET, checksum(raw));
        Files.write(target, raw, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    static boolean storedChecksumMatches(byte[] raw) {
        return getNot16(raw, CHECKSUM_OFFSET) == checksum(raw);
    }

    static int checksum(byte[] raw) {
        int sum = 0;
        for (int o = CHECKSUM_FROM; o < CHECKSUM_OFFSET; o += 2) sum += getNot16(raw, o);
        return sum & 0xFFFF;
    }

    private static int getNot16(byte[] raw, int offset) {
        return ~((raw[offset] & 0xFF) | ((raw[offset + 1] & 0xFF) << 8)) & 0xFFFF;
    }

    private static void putNot16(byte[] raw, int offset, int value) {
        int stored = ~value & 0xFFFF;
        raw[offset] = (byte) stored;
        raw[offset + 1] = (byte) (stored >>> 8);
    }
}
