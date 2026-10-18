package com.hottopik.service.exam;

import com.hottopik.entity.exam.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Ghi bộ đề mẫu ra thư mục để demo nhập đề:
 * {@code ./mvnw test -Dtest=SampleFilesGenerator -Dsamples.out=samples}
 */
@EnabledIfSystemProperty(named = "samples.out", matches = ".+")
class SampleFilesGenerator {

    @Test
    void writeSamples() throws IOException {
        Path dir = Path.of(System.getProperty("samples.out"));
        Files.createDirectories(dir);
        write(dir, Track.TOPIK_II, 1);
        write(dir, Track.TOPIK_II, 2);
        write(dir, Track.TOPIK_I, 1);
    }

    private static void write(Path dir, Track track, int variant) throws IOException {
        SampleExamFactory.Sample s = SampleExamFactory.full(track, variant);
        String base = (track == Track.TOPIK_I ? "TOPIK_I" : "TOPIK_II") + "_de_mau_" + variant;
        Files.write(dir.resolve(base + ".xlsx"), s.excel());
        try (OutputStream out = Files.newOutputStream(dir.resolve(base + "_media.zip")); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String a : s.audioFiles()) {
                zip.putNextEntry(new ZipEntry(a));
                zip.write(SampleExamFactory.fakeMp3());
                zip.closeEntry();
            }
            for (String i : s.imageFiles()) {
                zip.putNextEntry(new ZipEntry(i));
                zip.write(SampleExamFactory.fakePng());
                zip.closeEntry();
            }
        }
    }
}
