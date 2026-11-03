package com.vitalsafe.api.vitals.csv;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void plainTextIsLeftAlone() {
        assertThat(CsvWriter.field("after a walk")).isEqualTo("after a walk");
        assertThat(CsvWriter.field(null)).isEmpty();
    }

    @Test
    void fieldsThatNeedItAreQuotedAndQuotesAreDoubled() {
        assertThat(CsvWriter.field("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.field("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvWriter.field("one\ntwo")).isEqualTo("\"one\ntwo\"");
        assertThat(CsvWriter.field(" padded ")).isEqualTo("\" padded \"");
    }

    @Test
    void aLineJoinsFieldsWithCommas() {
        assertThat(CsvWriter.line(Arrays.asList("HEART_RATE", "72", "", "/min"))).isEqualTo("HEART_RATE,72,,/min");
        assertThat(CsvWriter.line(List.of("a,b", "c"))).isEqualTo("\"a,b\",c");
    }

    @Test
    void textThatWouldRunAsAFormulaIsNeutralised() {
        assertThat(CsvWriter.neutralise("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvWriter.neutralise("+cmd")).isEqualTo("'+cmd");
        assertThat(CsvWriter.neutralise("-5")).isEqualTo("'-5");
        assertThat(CsvWriter.neutralise("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvWriter.neutralise("\t=1")).isEqualTo("'\t=1");
    }

    @Test
    void ordinaryTextAndEmptyValuesAreNotChanged() {
        assertThat(CsvWriter.neutralise("morning")).isEqualTo("morning");
        assertThat(CsvWriter.neutralise("a=b")).isEqualTo("a=b");
        assertThat(CsvWriter.neutralise(null)).isEmpty();
        assertThat(CsvWriter.neutralise("")).isEmpty();
    }
}