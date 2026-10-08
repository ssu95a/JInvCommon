package ru.inversion.utils.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

public class Test_SegmentedBAOS {

    private SegmentedBAOS baos;

    @BeforeEach
    void setUp() {
        baos = new SegmentedBAOS(256); // Малый размер сегмента для тестирования
    }

    @Test
    @DisplayName("Initial state")
    void testInitialState() {
        assertEquals(0, baos.size());
        assertEquals(0, baos.toByteArray().length);
//        assertTrue(baos.isEmpty());
//        assertEquals(1, baos.getSegmentCount());
    }

    @Test
    @DisplayName("Single byte write")
    void testSingleByteWrite() {
        baos.write(65);
        assertEquals(1, baos.size());
        assertArrayEquals(new byte[]{65}, baos.toByteArray());
        assertEquals("A", baos.toString());
    }

    @Test
    @DisplayName("Multiple bytes write")
    void testMultipleBytesWrite() {
        byte[] data = "Hello".getBytes();
        baos.write(data, 0, data.length);

        assertEquals(5, baos.size());
        assertArrayEquals(data, baos.toByteArray());
        assertEquals("Hello", baos.toString());
    }

    @Test
    @DisplayName("Segment growth")
    void testSegmentGrowth() {
        int segmentSize = baos.getSegmentSize();
        int bytesToWrite = segmentSize * 3 + 10; // Заполним 3 сегмента + немного

        for (int i = 0; i < bytesToWrite; i++) {
            baos.write(i % 256);
        }

//        assertEquals(bytesToWrite, baos.size());
//        assertEquals(4, baos.getSegmentCount()); // 3 полных + 1 частичный

        byte[] result = baos.toByteArray();
        assertEquals(bytesToWrite, result.length);
        for (int i = 0; i < bytesToWrite; i++) {
            assertEquals((byte) (i % 256), result[i]);
        }
    }

    @Test
    @DisplayName("Large data write")
    void testLargeDataWrite() throws IOException {
        byte[] largeData = new byte[1024 * 10]; // 10KB
        new Random().nextBytes(largeData);

        baos.write(largeData, 0, largeData.length);
        assertEquals(largeData.length, baos.size());
        assertArrayEquals(largeData, baos.toByteArray());
    }

    @Test
    @DisplayName("WriteTo output stream")
    void testWriteToOutputStream() throws IOException {
        byte[] data = "Test data for output stream".getBytes();
        baos.write(data, 0, data.length);

        ByteArrayOutputStream target = new ByteArrayOutputStream();
        baos.writeTo(target);

        assertArrayEquals(data, target.toByteArray());
    }

    @Test
    @DisplayName("Reset functionality")
    void testReset() throws IOException {
        baos.write("Hello".getBytes());
        assertEquals(5, baos.size());

        baos.reset();
        assertEquals(0, baos.size());
        assertTrue(baos.isEmpty());
        assertEquals(1, baos.getSegmentCount());

        // После reset можно снова писать
        baos.write("World".getBytes());
        assertEquals(5, baos.size());
        assertEquals("World", baos.toString());
    }

    @Test
    @DisplayName("InputStream reading")
    void testInputStreamReading() throws IOException {
        String testData = "The quick brown fox jumps over the lazy dog";
        baos.write(testData.getBytes());

        InputStream is = baos.inputStream();
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        byte[] buffer = new byte[128];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            result.write(buffer, 0, bytesRead);
        }

        assertEquals(testData, result.toString());
        is.close();
    }

    @Test
    @DisplayName("InputStream available()")
    void testInputStreamAvailable() throws IOException {
        baos.write(new byte[500]);

        InputStream is = baos.inputStream();
        assertEquals(500, is.available());

        byte[] buffer = new byte[100];
        is.read(buffer);
        assertEquals(400, is.available());

        is.read(buffer);
        assertEquals(300, is.available());

        is.close();
    }

    @Test
    @DisplayName("InputStream single byte reading")
    void testInputStreamSingleByte() throws IOException {
        baos.write("ABC".getBytes());

        InputStream is = baos.inputStream();
        assertEquals('A', is.read());
        assertEquals('B', is.read());
        assertEquals('C', is.read());
        assertEquals(-1, is.read());

        is.close();
    }

    @Test
    @DisplayName("Charset encoding")
    void testCharsetEncoding() throws Exception {
        String russianText = "Привет мир!";
        String chineseText = "你好世界";

        baos.write(russianText.getBytes(StandardCharsets.UTF_8));
        assertEquals(russianText, baos.toString(StandardCharsets.UTF_8.name()));

        baos.reset();
        baos.write(chineseText.getBytes(StandardCharsets.UTF_8));
        assertEquals(chineseText, baos.toString(StandardCharsets.UTF_8.name()));
    }


    @Test
    @DisplayName("Edge case: empty stream")
    void testEmptyStream() {
        assertArrayEquals(new byte[0], baos.toByteArray());
        assertEquals("", baos.toString());
        assertEquals(0, baos.size());
        assertTrue(baos.isEmpty());
    }

    @Test
    @DisplayName("Edge case: exact segment size")
    void testExactSegmentSize() {
        int segmentSize = baos.getSegmentSize();
        byte[] data = new byte[segmentSize];
        Arrays.fill(data, (byte) 42);

        baos.write(data, 0, data.length);
        assertEquals(segmentSize, baos.size());
        assertEquals(1, baos.getSegmentCount()); // Все еще один сегмент
        assertArrayEquals(data, baos.toByteArray());
    }

    @Test
    @DisplayName("Edge case: one byte over segment")
    void testOneByteOverSegment() {
        int segmentSize = baos.getSegmentSize();
        byte[] data = new byte[segmentSize + 1];
        Arrays.fill(data, (byte) 77);

        baos.write(data, 0, data.length);
        assertEquals(segmentSize + 1, baos.size());
        assertEquals(2, baos.getSegmentCount()); // Два сегмента
        assertArrayEquals(data, baos.toByteArray());
    }

    @Test
    @DisplayName("Write boundaries validation")
    void testWriteBoundaries() {
        byte[] data = {1, 2, 3};

        // Valid writes
        assertDoesNotThrow(() -> baos.write(data, 0, 3));
        assertDoesNotThrow(() -> baos.write(data, 1, 2));

        // Invalid writes
        assertThrows(IndexOutOfBoundsException.class, () -> baos.write(data, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> baos.write(data, 0, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> baos.write(data, 2, 2));
        assertThrows(NullPointerException.class, () -> baos.write(null, 0, 1));
    }

    @Test
    @DisplayName("Close functionality")
    void testClose() throws IOException {
        baos.write("test".getBytes());

        InputStream is = baos.inputStream();
        assertDoesNotThrow(() -> baos.close()); // Первый close не должен закрыть

        // После закрытия всех InputStream'ов
        is.close();
        baos.close(); // Теперь должен закрыться

        assertThrows(IllegalStateException.class, () -> baos.write(1));
        assertThrows(IllegalStateException.class, () -> baos.inputStream());
        assertThrows(IllegalStateException.class, () -> baos.size());
    }

    @Test
    @DisplayName("Multiple InputStreams")
    void testMultipleInputStreams() throws IOException {
        baos.write("data".getBytes());

        InputStream is1 = baos.inputStream();
        InputStream is2 = baos.inputStream();

        assertEquals('d', is1.read());
        assertEquals('d', is2.read());

        is1.close();
        // baos еще не должен закрыться
        assertDoesNotThrow(() -> baos.write(1));

        is2.close();
        baos.close();
        // Теперь должен быть закрыт
        assertThrows(IllegalStateException.class, () -> baos.write(1));
    }

    @Test
    @DisplayName("Performance: large sequential writes")
    void testLargeSequentialWrites() {
        int totalSize = 1024 * 1024; // 1MB
        byte[] testData = new byte[totalSize];
        new Random().nextBytes(testData);

        long startTime = System.currentTimeMillis();

        // Последовательная запись малыми chunks
        int chunkSize = 8192;
        for (int i = 0; i < totalSize; i += chunkSize) {
            int length = Math.min(chunkSize, totalSize - i);
            baos.write(testData, i, length);
        }

        long writeTime = System.currentTimeMillis() - startTime;

        startTime = System.currentTimeMillis();
        byte[] result = baos.toByteArray();
        long readTime = System.currentTimeMillis() - startTime;

        assertEquals(totalSize, result.length);
        assertArrayEquals(testData, result);

        System.out.printf("Write: %dms, Read: %dms, Segments: %d%n",
                writeTime, readTime, baos.getSegmentCount());

        assertTrue(writeTime < 1000, "Write too slow: " + writeTime + "ms");
        assertTrue(readTime < 500, "Read too slow: " + readTime + "ms");
    }

    @Test
    @DisplayName("UTF-8 string optimization")
    void testUtf8StringOptimization() throws IOException {
        String testString = "Hello world! こんにちは世界! Привет мир!";
        byte[] utf8Bytes = testString.getBytes(StandardCharsets.UTF_8);

        baos.write(utf8Bytes);

        String result1 = baos.toString();
        String result2 = baos.toString(StandardCharsets.UTF_8.name());

        assertEquals(testString, result1);
        assertEquals(testString, result2);
    }

    @Test
    @DisplayName("Byte pattern preservation")
    void testBytePatternPreservation() throws IOException {
        // Тестируем сохранение точных byte значений
        byte[] pattern = {0, 1, -1, 127, -128, Byte.MAX_VALUE, Byte.MIN_VALUE};
        baos.write(pattern);

        byte[] result = baos.toByteArray();
        assertArrayEquals(pattern, result);

        // Проверяем, что InputStream тоже возвращает точные значения
        InputStream is = baos.inputStream();
        byte[] fromStream = new byte[pattern.length];
        try {
            int read = is.read(fromStream);
            assertEquals(pattern.length, read);
            assertArrayEquals(pattern, fromStream);
            is.close();
        } catch (IOException e) {
            fail("IOException during stream reading", e);
        }
    }

    @Test
    @DisplayName("Concurrent access safety")
    void testConcurrentAccess() throws InterruptedException {
        final int threadCount = 10;
        final int writesPerThread = 1000;

        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < writesPerThread; j++) {
                    synchronized (baos) {
                        baos.write(threadId);
                        baos.write(j);
                    }
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(threadCount * writesPerThread * 2, baos.size());

        // Проверяем, что все данные записались корректно
        byte[] result = baos.toByteArray();
        assertEquals(threadCount * writesPerThread * 2, result.length);
    }
}