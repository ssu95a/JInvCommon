package ru.inversion.utils.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Test_SegmentedCAW {

    private SegmentedCAW caw;

    @BeforeEach
    void setUp() {
        caw = new SegmentedCAW(256); // Малый размер сегмента для тестирования
    }

    @Test
    @DisplayName("Initial state")
    void testInitialState() {
        assertEquals(0, caw.size());
        assertEquals(0, caw.toCharArray().length);
        assertEquals("", caw.toString());
    }

    @Test
    @DisplayName("Single char write")
    void testSingleCharWrite() {
        caw.write('A');
        assertEquals(1, caw.size());
        assertArrayEquals(new char[]{'A'}, caw.toCharArray());
        assertEquals("A", caw.toString());
    }

    @Test
    @DisplayName("Special characters optimization")
    void testSpecialCharactersOptimization() {
        caw.write('\n');
        caw.write(' ');
        caw.write('X');

        assertEquals(3, caw.size());
        assertEquals("\n X", caw.toString());
    }

    @Test
    @DisplayName("String write")
    void testStringWrite() throws IOException {
        String testString = "Hello World";
        caw.write(testString);

        assertEquals(testString.length(), caw.size());
        assertEquals(testString, caw.toString());
        assertArrayEquals(testString.toCharArray(), caw.toCharArray());
    }

    @Test
    @DisplayName("Char array write")
    void testCharArrayWrite() {
        char[] data = {'H', 'e', 'l', 'l', 'o'};
        caw.write(data, 0, data.length);

        assertEquals(5, caw.size());
        assertArrayEquals(data, caw.toCharArray());
        assertEquals("Hello", caw.toString());
    }

    @Test
    @DisplayName("Partial char array write")
    void testPartialCharArrayWrite() {
        char[] data = {'A', 'B', 'C', 'D', 'E'};
        caw.write(data, 1, 3); // B, C, D

        assertEquals(3, caw.size());
        assertEquals("BCD", caw.toString());
    }

    @Test
    @DisplayName("Segment growth with chars")
    void testSegmentGrowthWithChars() {
        int segmentSize = 256;
        int charsToWrite = segmentSize * 3 + 10;

        for (int i = 0; i < charsToWrite; i++) {
            caw.write((char) ('A' + (i % 26)));
        }

        assertEquals(charsToWrite, caw.size());

        char[] result = caw.toCharArray();
        assertEquals(charsToWrite, result.length);
        for (int i = 0; i < charsToWrite; i++) {
            assertEquals((char) ('A' + (i % 26)), result[i]);
        }
    }

    @Test
    @DisplayName("Mixed content types")
    void testMixedContentTypes() throws IOException {
        caw.write('H');
        caw.write("ello");
        caw.write(' ');
        caw.write("World".toCharArray(), 0, 5);
        caw.write('!');

        assertEquals(12, caw.size());
        assertEquals("Hello World!", caw.toString());
    }

    @Test
    @DisplayName("Reader integration")
    void testReaderIntegration() throws IOException {
        String testData = "The quick brown fox jumps over the lazy dog";
        caw.write(testData);

        Reader reader = caw.reader();
        char[] buffer = new char[1024];
        int bytesRead = reader.read(buffer);

        assertEquals(testData.length(), bytesRead);
        assertEquals(testData, new String(buffer, 0, bytesRead));
        reader.close();
    }

    @Test
    @DisplayName("Reader single char reading")
    void testReaderSingleChar() throws IOException {
        caw.write("ABC");

        Reader reader = caw.reader();
        assertEquals('A', reader.read());
        assertEquals('B', reader.read());
        assertEquals('C', reader.read());
        assertEquals(-1, reader.read());
        reader.close();
    }

    @Test
    @DisplayName("Reader available() simulation")
    void testReaderAvailable() throws IOException {
        caw.write(new char[500]);

        Reader reader = caw.reader();
        // Note: Reader doesn't have available(), but we can test reading
        char[] buffer = new char[100];
        int read = reader.read(buffer);
        assertEquals(100, read);

        read = reader.read(buffer);
        assertEquals(100, read);
        reader.close();
    }

    @Test
    @DisplayName("WriteTo writer")
    void testWriteToWriter() throws IOException {
        String testData = "Test data for writer";
        caw.write(testData);

        StringWriter writer = new StringWriter();
        caw.writeTo(writer);

        assertEquals(testData, writer.toString());
    }

    @Test
    @DisplayName("Reset functionality")
    void testReset() throws IOException {
        caw.write("Hello");
        assertEquals(5, caw.size());

        caw.reset();
        assertEquals(0, caw.size());
        assertEquals("", caw.toString());

        // After reset should work again
        caw.write("World");
        assertEquals(5, caw.size());
        assertEquals("World", caw.toString());
    }

    @Test
    @DisplayName("Empty and null handling")
    void testEmptyAndNullHandling() throws IOException {
        // Empty string
        caw.write("");
        assertEquals(0, caw.size());

        // Null reader
        caw.write((Reader) null);
        assertEquals(0, caw.size());

        // Empty char array
        caw.write(new char[0], 0, 0);
        assertEquals(0, caw.size());
    }

    @Test
    @DisplayName("Boundary validation")
    void testBoundaryValidation() {
        char[] data = {'A', 'B', 'C'};

        // Valid writes
        assertDoesNotThrow(() -> caw.write(data, 0, 3));
        assertDoesNotThrow(() -> caw.write(data, 1, 2));

        // Invalid writes
        assertThrows(IndexOutOfBoundsException.class, () -> caw.write(data, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> caw.write(data, 0, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> caw.write(data, 2, 2));
        //assertThrows(IndexOutOfBoundsException.class, () -> caw.write((String)null, 0, 1));
        //assertThrows(IndexOutOfBoundsException.class, () -> caw.write((char[])null, 0, 1));
    }

    @Test
    @DisplayName("Close functionality")
    void testClose() throws IOException {
        caw.write("test");

        Reader reader = caw.reader();
        assertDoesNotThrow(() -> caw.close()); // First close shouldn't close

        reader.close();
        caw.close(); // Now should close

        assertThrows(IllegalStateException.class, () -> caw.write('A'));
        assertThrows(IllegalStateException.class, () -> caw.reader());
    }

    @Test
    @DisplayName("Multiple readers")
    void testMultipleReaders() throws IOException {
        caw.write("data");

        Reader reader1 = caw.reader();
        Reader reader2 = caw.reader();

        assertEquals('d', reader1.read());
        assertEquals('d', reader2.read());

        reader1.close();
        // caw should still work
        assertDoesNotThrow(() -> caw.write('X'));

        reader2.close();
        caw.close();
        assertThrows(IllegalStateException.class, () -> caw.write('Y'));
    }

    @Test
    @DisplayName("Large text data")
    void testLargeTextData() throws IOException {
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeText.append("Line ").append(i).append("\n");
        }

        caw.write(largeText.toString());
        assertEquals(largeText.length(), caw.size());
        assertEquals(largeText.toString(), caw.toString());
    }

    @Test
    @DisplayName("Unicode characters")
    void testUnicodeCharacters() throws IOException {
        String unicodeText = "Hello 世界 Привет 🌍";
        caw.write(unicodeText);

        assertEquals(unicodeText.length(), caw.size());
        assertEquals(unicodeText, caw.toString());
    }

    @Test
    @DisplayName("Write from reader")
    void testWriteFromReader() throws IOException {
        String sourceText = "Text from reader content";
        StringReader sourceReader = new StringReader(sourceText);

        caw.write(sourceReader);

        assertEquals(sourceText.length(), caw.size());
        assertEquals(sourceText, caw.toString());
    }

    @Test
    @DisplayName("Performance: mixed operations")
    void testPerformanceMixedOperations() throws IOException {
        long startTime = System.currentTimeMillis();

        int operations = 10000;
        Random random = new Random();

        for (int i = 0; i < operations; i++) {
            int operation = random.nextInt(4);
            switch (operation) {
                case 0:
                    caw.write(random.nextInt(26) + 'A');
                    break;
                case 1:
                    caw.write("test");
                    break;
                case 2:
                    char[] chars = new char[random.nextInt(10) + 1];
                    Arrays.fill(chars, (char) (random.nextInt(26) + 'A'));
                    caw.write(chars, 0, chars.length);
                    break;
                case 3:
                    if (random.nextDouble() < 0.1) {
                        caw.toString();
                    }
                    break;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Mixed operations: " + operations + " in " + duration + "ms");
        assertTrue(duration < 1000, "Operations too slow: " + duration + "ms");
    }

    @Test
    @DisplayName("Edge case: exact segment size")
    void testExactSegmentSize() {
        int segmentSize = 256;
        SegmentedCAW exactCaw = new SegmentedCAW(segmentSize);

        for (int i = 0; i < segmentSize; i++) {
            exactCaw.write('X');
        }

        assertEquals(segmentSize, exactCaw.size());
        assertEquals(segmentSize, exactCaw.toCharArray().length);
    }

    @Test
    @DisplayName("Edge case: one char over segment")
    void testOneCharOverSegment() {
        int segmentSize = 256;
        SegmentedCAW overflowCaw = new SegmentedCAW(segmentSize);

        for (int i = 0; i < segmentSize + 1; i++) {
            overflowCaw.write('Y');
        }

        assertEquals(segmentSize + 1, overflowCaw.size());
        assertEquals(segmentSize + 1, overflowCaw.toCharArray().length);
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
                    caw.write((char) ('A' + threadId));
                    caw.write((char) ('0' + (j % 10)));
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(threadCount * writesPerThread * 2, caw.size());

        // Verify all data was written correctly
        String result = caw.toString();
        assertEquals(threadCount * writesPerThread * 2, result.length());
    }

    @Test
    @DisplayName("Concurrent write and read without deadlocks")
    void testConcurrentWriteRead() throws IOException, InterruptedException {
        final int writeCount = 10000;
        final CountDownLatch writeDone = new CountDownLatch(1);
        final CountDownLatch readDone = new CountDownLatch(1);
        final AtomicInteger readTotal = new AtomicInteger(0);

        // Writer thread
        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < writeCount; i++) {
                    caw.write((char) ('A' + (i % 26)));
                    if (i % 1000 == 0) {
                        Thread.yield(); // Даем chance reader'у
                    }
                }
                writeDone.countDown();
            } catch (Exception e) {
                fail("Writer failed: " + e.getMessage());
            }
        });

        // Reader thread
        Thread reader = new Thread(() -> {
            try {
                Reader r = caw.reader();
                char[] buffer = new char[1024];
                int total = 0;
                int read;

                while (total < writeCount && !writeDone.await(100, TimeUnit.MILLISECONDS)) {
                    read = r.read(buffer);
                    if (read > 0) {
                        total += read;
                    } else {
                        Thread.sleep(10);
                    }
                }

                // Дорешиваем остатки
                while ((read = r.read(buffer)) > 0) {
                    total += read;
                }

                readTotal.set(total);
                r.close();
                readDone.countDown();
            } catch (Exception e) {
                fail("Reader failed: " + e.getMessage());
            }
        });

        writer.start();
        reader.start();

        assertTrue(writeDone.await(5000, TimeUnit.MILLISECONDS), "Writer timeout");
        assertTrue(readDone.await(5000, TimeUnit.MILLISECONDS), "Reader timeout");

        assertEquals(writeCount, readTotal.get());
        assertEquals(writeCount, caw.size());
    }
}