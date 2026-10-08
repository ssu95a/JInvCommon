package ru.inversion.utils.converter;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;

public class Test_TypeConverter {

    @BeforeAll
    static void setup() {
    }

    // Вспомогательные методы для создания тестовых данных
    private static Blob createTestBlob(String content) throws SQLException {
        return new SerialBlob(content.getBytes());
    }

    /** */
    private static Clob createTestClob(String content) throws SQLException {
        return new SerialClob(content.toCharArray());
    }

    @Test
    @DisplayName("Number conversions - Boolean")
    void testBooleanConversions() {
        // Long <-> Boolean
        assertEquals(Boolean.TRUE, TypeConverter.convert(1L, Boolean.class));
        assertEquals(Boolean.FALSE, TypeConverter.convert(0L, Boolean.class));
        assertEquals(1L, TypeConverter.convert(Boolean.TRUE, Long.class));
        assertEquals(0L, TypeConverter.convert(Boolean.FALSE, Long.class));

        // Integer <-> Boolean
        assertEquals(Boolean.TRUE, TypeConverter.convert(1, Boolean.class));
        assertEquals(Boolean.FALSE, TypeConverter.convert(0, Boolean.class));

        // BigDecimal <-> Boolean
        assertEquals(Boolean.TRUE, TypeConverter.convert(BigDecimal.ONE, Boolean.class));
        assertEquals(Boolean.FALSE, TypeConverter.convert(BigDecimal.ZERO, Boolean.class));

        // String <-> Boolean
        assertEquals(Boolean.TRUE, TypeConverter.convert("true", Boolean.class));
        assertEquals(Boolean.TRUE, TypeConverter.convert("1", Boolean.class));
        assertEquals(Boolean.TRUE, TypeConverter.convert("yes", Boolean.class));
        assertEquals(Boolean.TRUE, TypeConverter.convert("да", Boolean.class));
        assertEquals(Boolean.FALSE, TypeConverter.convert("false", Boolean.class));
        assertEquals(Boolean.FALSE, TypeConverter.convert("0", Boolean.class));
    }

    @Test
    @DisplayName("Number conversions - Numeric types")
    void testNumericConversions() {
        // Integer <-> Long
        assertEquals(100L, TypeConverter.convert(100, Long.class));
        assertEquals(Integer.valueOf(100), TypeConverter.convert(100L, Integer.class));

        // Integer <-> Byte
        assertEquals(Byte.valueOf((byte) 100), TypeConverter.convert(100, Byte.class));
        assertEquals(Integer.valueOf(100), TypeConverter.convert((byte) 100, Integer.class));

        // BigDecimal <-> Long
        assertEquals(100L, TypeConverter.convert(new BigDecimal("100"), Long.class));
        assertEquals(new BigDecimal("100"), TypeConverter.convert(100L, BigDecimal.class));

        // BigDecimal <-> Integer
        assertEquals(100, TypeConverter.convert(new BigDecimal("100"), Integer.class));
        assertEquals(new BigDecimal("100"), TypeConverter.convert(100, BigDecimal.class));

        // BigDecimal <-> BigInteger
        assertEquals(new BigInteger("100"), TypeConverter.convert(new BigDecimal("100"), BigInteger.class));
        assertEquals(new BigDecimal("100"), TypeConverter.convert(new BigInteger("100"), BigDecimal.class));
    }

    @Test
    @DisplayName("String to Number conversions")
    void testStringToNumberConversions() {
        // Simple integers
        assertEquals(123, TypeConverter.convert("123", Integer.class));
        assertEquals(123L, TypeConverter.convert("123", Long.class));

        // Large numbers
        assertEquals(new BigInteger("12345678901234567890"),
                TypeConverter.convert("12345678901234567890", BigInteger.class));

        // Decimal numbers
        assertEquals(new BigDecimal("123.45"), TypeConverter.convert("123.45", BigDecimal.class));
        assertEquals(new BigDecimal("123.45"), TypeConverter.convert("123,45", BigDecimal.class));

        // With spaces and signs
        assertEquals(new BigDecimal("-123.45"), TypeConverter.convert(" -123.45 ", BigDecimal.class));
        assertEquals(new BigDecimal("123.45"), TypeConverter.convert(" +123.45 ", BigDecimal.class));

        // Edge cases
        assertNull(TypeConverter.convert("", Number.class));
        assertNull(TypeConverter.convert("   ", Number.class));
        assertNull(TypeConverter.convert(null, Number.class));
    }

    @Test
    @DisplayName("String to Number conversion errors")
    void testStringToNumberConversionErrors() {
        assertThrows(TypeConverterException.class, () -> TypeConverter.convert("abc", Number.class));
        assertThrows(TypeConverterException.class, () -> TypeConverter.convert("123.45.67", Number.class));
    }

    @Test
    @DisplayName("Date and Time conversions")
    void testDateTimeConversions() throws SQLException {
        java.util.Date nowUtil = new java.util.Date();
        java.sql.Date nowSql = new java.sql.Date(nowUtil.getTime());
        LocalDate today = LocalDate.now();
        LocalDateTime nowDateTime = LocalDateTime.now();

        // java.util.Date <-> LocalDate
        LocalDate convertedLocalDate = TypeConverter.convert(nowUtil, LocalDate.class);
        assertNotNull(convertedLocalDate);

        // java.util.Date <-> LocalDateTime
        LocalDateTime convertedLocalDateTime = TypeConverter.convert(nowUtil, LocalDateTime.class);
        assertNotNull(convertedLocalDateTime);

        // java.sql.Date <-> LocalDate
        assertEquals(today, TypeConverter.convert(nowSql, LocalDate.class));
        assertEquals(nowSql, TypeConverter.convert(today, java.sql.Date.class));

        // java.sql.Date <-> java.util.Date
        assertEquals(nowUtil, TypeConverter.convert(nowSql, java.util.Date.class));
        assertEquals(nowSql, TypeConverter.convert(nowUtil, java.sql.Date.class));

        // Timestamp <-> LocalDateTime
        Timestamp timestamp = Timestamp.valueOf(nowDateTime);
        assertEquals(nowDateTime, TypeConverter.convert(timestamp, LocalDateTime.class));
        assertEquals(timestamp, TypeConverter.convert(nowDateTime, Timestamp.class));

        // String <-> LocalDate
        assertEquals(LocalDate.of(2023, 12, 21),
                TypeConverter.convert("21.12.2023", LocalDate.class));
        assertEquals("21.12.2023",
                TypeConverter.convert(LocalDate.of(2023, 12, 21), String.class));

        // String <-> LocalDateTime
        LocalDateTime testDateTime = LocalDateTime.of(2023, 12, 21, 10, 11, 3);
        assertEquals(testDateTime,
                TypeConverter.convert("21.12.2023 10:11:03", LocalDateTime.class));
    }

    @Test
    @DisplayName("Date format conversions")
    void testDateFormatConversions() {
        LocalDate date = LocalDate.of(2023, 12, 21);
        LocalDateTime dateTime = LocalDateTime.of(2023, 12, 21, 14, 30, 45);

        // Format masks
        assertEquals("21/12/2023", TypeConverter.convertToString(date, "dd/MM/yyyy"));
        assertEquals("2023-12-21", TypeConverter.convertToString(date, "yyyy-MM-dd"));
        assertEquals("21.12.2023 14:30:45", TypeConverter.convertToString(dateTime, "dd.MM.yyyy HH:mm:ss"));
    }

    @Test
    @DisplayName("SQL and IO type conversions")
    void testSQLAndIOConversions() throws SQLException, IOException {
        String testContent = "Hello, World! Тестовая строка";

        // Blob <-> String
        Blob blob = createTestBlob(testContent);
        assertEquals(testContent, TypeConverter.convert(blob, String.class));

        Blob convertedBlob = TypeConverter.convert(testContent, Blob.class);
        assertNotNull(convertedBlob);
        assertEquals(testContent.length(), convertedBlob.length());

        // Clob <-> String
        Clob clob = createTestClob(testContent);
        assertEquals(testContent, TypeConverter.convert(clob, String.class));

        // Blob <-> byte[]
        byte[] bytes = TypeConverter.convert(blob, byte[].class);
        assertNotNull(bytes);
        assertEquals(testContent, new String(bytes));

        // Reader <-> String
        StringReader reader = new StringReader(testContent);
        assertEquals(testContent, TypeConverter.convert(reader, String.class));

        // InputStream <-> byte[]
        ByteArrayInputStream inputStream = new ByteArrayInputStream(testContent.getBytes());
        byte[] convertedBytes = TypeConverter.convert(inputStream, byte[].class);
        assertEquals(testContent, new String(convertedBytes));
    }

    @Test
    @DisplayName("Character conversions")
    void testCharacterConversions() {
        // String <-> Character
        assertEquals('A', TypeConverter.convert("A", Character.class));
        assertEquals("A", TypeConverter.convert('A', String.class));

        // Edge cases for Character
        assertThrows(TypeConverterException.class, () -> TypeConverter.convert("AB", Character.class));
        assertEquals(Character.valueOf((char) 0), TypeConverter.convert("", Character.class));
    }

    @Test
    @DisplayName("Multithreaded Date conversions")
    void testMultithreadedDateConversions() throws InterruptedException, ExecutionException {
        final int threadCount = 50;
        final int iterations = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        LocalDate testDate = LocalDate.of(2023, 12, 21);
        String expectedString = "21.12.2023";

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                for (int j = 0; j < iterations; j++) {
                    // Тестируем преобразования, которые используют не потокобезопасные SimpleDateFormat
                    String result = TypeConverter.convert(testDate, String.class);
                    if (!expectedString.equals(result)) {
                        return false;
                    }

                    LocalDate convertedBack = TypeConverter.convert(expectedString, LocalDate.class);
                    if (!testDate.equals(convertedBack)) {
                        return false;
                    }

                    // Форматированные преобразования
                    String formatted = TypeConverter.convertToString(testDate, "yyyy/MM/dd");
                    if (!"2023/12/21".equals(formatted)) {
                        return false;
                    }
                }
                return true;
            });
        }

        List<Future<Boolean>> results = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Boolean> result : results) {
            assertTrue(result.get(), "Multithreaded date conversion failed");
        }
    }

    @Test
    @DisplayName("Multithreaded Number conversions")
    void testMultithreadedNumberConversions() throws InterruptedException, ExecutionException {
        final int threadCount = 50;
        final int iterations = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        BigDecimal testNumber = new BigDecimal("12345.67");
        String expectedString = "12345.67";

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                for (int j = 0; j < iterations; j++) {
                    // Тестируем числовые преобразования
                    String result = TypeConverter.convert(testNumber, String.class);
                    if (!expectedString.equals(result)) {
                        return false;
                    }

                    BigDecimal convertedBack = TypeConverter.convert(expectedString, BigDecimal.class);
                    if (!testNumber.equals(convertedBack)) {
                        return false;
                    }

                    // Boolean conversions
                    Boolean boolResult = TypeConverter.convert(1L, Boolean.class);
                    if (!Boolean.TRUE.equals(boolResult)) {
                        return false;
                    }
                }
                return true;
            });
        }

        List<Future<Boolean>> results = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Boolean> result : results) {
            assertTrue(result.get(), "Multithreaded number conversion failed");
        }
    }

    @Test
    @DisplayName("Multithreaded String conversions with format")
    void testMultithreadedStringFormatConversions() throws InterruptedException, ExecutionException {
        final int threadCount = 20;
        final int iterations = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        java.sql.Date testDate = java.sql.Date.valueOf("2023-12-21");

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                for (int j = 0; j < iterations; j++) {
                    // Тестируем форматированные преобразования, которые могут использовать не потокобезопасные форматеры
                    try {
                        String formatted = TypeConverter.convertToString(testDate, "dd.MM.yyyy");
                        if (!"21.12.2023".equals(formatted)) {
                            return false;
                        }

                        // Обратное преобразование
                        java.sql.Date convertedBack = TypeConverter.convert("21.12.2023", java.sql.Date.class);
                        if (!testDate.equals(convertedBack)) {
                            return false;
                        }
                    } catch (Exception e) {
                        return false;
                    }
                }
                return true;
            });
        }

        List<Future<Boolean>> results = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Boolean> result : results) {
            assertTrue(result.get(), "Multithreaded string format conversion failed");
        }
    }

    @Test
    @DisplayName("Null and edge case handling")
    void testNullAndEdgeCases() {
        // Null conversions
        assertNull(TypeConverter.convert(null, String.class));
        assertNull(TypeConverter.convert(null, Integer.class));
        assertNull(TypeConverter.convert(null, LocalDate.class));

        // Empty string conversions
        assertNull(TypeConverter.convert("", Integer.class));
        assertNull(TypeConverter.convert("   ", BigDecimal.class));

        // Same type conversion
        String testString = "test";
        assertEquals(testString, TypeConverter.convert(testString, String.class));

        // Object type conversion
        assertEquals(testString, TypeConverter.convert(testString, Object.class));
    }

    @Test
    @DisplayName("Error handling and exception messages")
    void testErrorHandling() {
        // Invalid number conversion
        TypeConverterException exception = assertThrows(TypeConverterException.class,
                () -> TypeConverter.convert("not_a_number", Integer.class));
        assertTrue(exception.getMessage().contains("Integer"));
        assertTrue(exception.getMessage().contains("not_a_number"));

        // Invalid date conversion
        exception = assertThrows(TypeConverterException.class,
                () -> TypeConverter.convert("invalid_date", LocalDate.class));
        assertTrue(exception.getMessage().contains("LocalDate"));
        assertTrue(exception.getMessage().contains("invalid_date"));

        // Unsupported conversion
        assertThrows(ClassCastException.class,
                () -> TypeConverter.convert("test", java.util.List.class));
    }

    private enum TestEnum { VALUE1, VALUE2, VALUE3 }

    @Test
    @DisplayName("Enum conversions")
    void testEnumConversions() {

        // String to Enum
        assertEquals(TestEnum.VALUE1, TypeConverter.convert("VALUE1", TestEnum.class));
        assertEquals(TestEnum.VALUE2, TypeConverter.convert("VALUE2", TestEnum.class));

        // Enum to String
        assertEquals("VALUE1", TypeConverter.convert(TestEnum.VALUE1, String.class));
        assertEquals("VALUE3", TypeConverter.convert(TestEnum.VALUE3, String.class));

        // Invalid enum value
        assertThrows(TypeConverterException.class,
                () -> TypeConverter.convert("INVALID_VALUE", TestEnum.class));
    }

    @Test
    @DisplayName("Performance test for common conversions")
    void testPerformance() {
        int iterations = 10000;
        LocalDate testDate = LocalDate.of(2023, 12, 21);
        BigDecimal testNumber = new BigDecimal("12345.6789");

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            TypeConverter.convert(testDate, String.class);
            TypeConverter.convert("21.12.2023", LocalDate.class);
            TypeConverter.convert(testNumber, String.class);
            TypeConverter.convert("12345.6789", BigDecimal.class);
            TypeConverter.convert(1L, Boolean.class);
            TypeConverter.convert(true, Long.class);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("Performance test: " + iterations + " iterations took " + duration + "ms");
        assertTrue(duration < 5000, "Performance test took too long: " + duration + "ms");
    }
}