package com.vitalsafe.api.vitals.csv;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvReaderTest {

    private static List<List<String>> parse(String text) {
        return CsvReader.parse(text, 100, 10, 1000);
    }

    @Test
    void readsPlainRows() {
        assertThat(parse("a,b,c\n1,2,3\n")).containsExactly(List.of("a", "b", "c"), List.of("1", "2", "3"));
    }

    @Test
    void aTrailingLineBreakDoesNotAddARow() {
        assertThat(parse("a,b\n1,2\n")).hasSize(2);
        assertThat(parse("a,b\n1,2")).hasSize(2);
    }

    @Test
    void windowsAndOldMacLineEndingsAreAccepted() {
        assertThat(parse("a,b\r\n1,2\r\n")).containsExactly(List.of("a", "b"), List.of("1", "2"));
        assertThat(parse("a,b\r1,2\r")).containsExactly(List.of("a", "b"), List.of("1", "2"));
    }

    @Test
    void quotedFieldsKeepCommasQuotesAndLineBreaks() {
        List<List<String>> rows = parse("note\n\"one, two\"\n\"say \"\"hi\"\"\"\n\"line one\nline two\"\n");

        assertThat(rows.get(1)).containsExactly("one, two");
        assertThat(rows.get(2)).containsExactly("say \"hi\"");
        assertThat(rows.get(3)).containsExactly("line one\nline two");
    }

    @Test
    void emptyFieldsAreKept() {
        assertThat(parse("a,,c\n,,\n")).containsExactly(List.of("a", "", "c"), List.of("", "", ""));
    }

    @Test
    void aTrailingCommaAddsAnEmptyField() {
        assertThat(parse("a,b,")).containsExactly(List.of("a", "b", ""));
    }

    @Test
    void aBlankLineBecomesARowWithOneEmptyField() {
        assertThat(parse("a\n\nb\n")).containsExactly(List.of("a"), List.of(""), List.of("b"));
    }

    @Test
    void aQuoteInsideAnUnquotedFieldIsRefused() {
        assertThatThrownBy(() -> parse("a,b\"c\n"))
                .isInstanceOf(CsvReader.CsvFormatException.class)
                .hasMessageContaining("Row 1");
    }

    @Test
    void textAfterAClosingQuoteIsRefused() {
        assertThatThrownBy(() -> parse("a\n\"b\"c\n"))
                .isInstanceOf(CsvReader.CsvFormatException.class)
                .hasMessageContaining("Row 2");
    }

    @Test
    void anUnterminatedQuoteIsRefused() {
        assertThatThrownBy(() -> parse("a\n\"never closed"))
                .isInstanceOf(CsvReader.CsvFormatException.class)
                .hasMessageContaining("quoted field");
    }

    @Test
    void tooManyRowsAreRefused() {
        assertThat(CsvReader.parse("a\n1\n2\n", 3, 10, 100)).hasSize(3);
        assertThatThrownBy(() -> CsvReader.parse("a\n1\n2\n3\n", 3, 10, 100))
                .isInstanceOf(CsvReader.CsvFormatException.class)
                .hasMessageContaining("more than 2 data rows");
    }

    @Test
    void tooManyColumnsAreRefused() {
        assertThatThrownBy(() -> CsvReader.parse("a,b,c,d\n", 10, 3, 100))
                .isInstanceOf(CsvReader.CsvFormatException.class)
                .hasMessageContaining("more than 3 columns");
    }

    @Test
    void aTooLongFieldIsRefused() {
        assertThatThrownBy(() -> CsvReader.parse("a\n" + "x".repeat(101) + "\n", 10, 3, 100))
                .isInstanceOf(CsvReader.CsvFormatException.class)
                .hasMessageContaining("longer than 100");
    }

    @Test
    void theErrorMessageNeverRepeatsTheFilesContent() {
        assertThatThrownBy(() -> parse("a,secret\"value\n"))
                .isInstanceOf(CsvReader.CsvFormatException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret"));
    }

    @Test
    void decodingDropsAByteOrderMark() {
        byte[] bytes = "\uFEFFa,b".getBytes(StandardCharsets.UTF_8);

        assertThat(CsvReader.decode(bytes)).isEqualTo("a,b");
    }

    @Test
    void decodingRefusesInvalidUtf8() {
        byte[] invalid = {(byte) 0xC3, (byte) 0x28};

        assertThatThrownBy(() -> CsvReader.decode(invalid))
                .isInstanceOf(CsvReader.CsvFormatException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void decodingRefusesBinaryData() {
        assertThatThrownBy(() -> CsvReader.decode(new byte[]{'a', 0, 'b'}))
                .isInstanceOf(CsvReader.CsvFormatException.class)
                .hasMessageContaining("binary");
    }
}