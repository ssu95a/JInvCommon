package ru.inversion.utils;

import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Test_Cleaner {

    static private class T {

        final Object leakFor = new Object();
        final FileInputStream ff;

        public T() throws Exception {

            FileInputStream f = new FileInputStream("d:\\car_model.csv");
            Cleaner.closeLater( leakFor, f );
            FileInputStream f1 = new FileInputStream("d:\\dba_role_privs.csv");
            Cleaner.closeLater( leakFor, f1 );

            ff=f1;

            Cleaner.closeLater( leakFor, new FileInputStream("d:\\dm_rkl_24122024_46.csv") );
            Cleaner.closeLater( leakFor, new AutoCloseable() {
                @Override
                public void close() throws Exception {
                    ff.close();
                    System.out.println("close !!!");
                }
            });
        }

    }

    @Test
    void closeLater( ) {
        try {

            int j = 0;

            do {

                {
                    T t = new T();
                }

                Thread.sleep(100);

                if( j++ % 100 == 0 ) {

                    System.out.println(j);

                    System.gc();
                    Thread.sleep(1000);

//                    System.out.println(j);
//                    for( int i = 0; i < 5; i++ ) {
//                        System.gc();
//                        Thread.sleep(1000);
//                    }
                }

            }while( 1 > 0);

        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void createAndRegisterResource() {
        Object resource = new Object();
        Cleaner.closeLater(resource, () -> {
            System.out.println("✅ Resource closed!");
        });
        // resource становится unreachable после выхода
    }
    @Test
    void testCloseLater() throws InterruptedException {
        System.out.println("Starting cleaner test...");

        // Создаем и сразу теряем ссылку
        createAndRegisterResource();

        // Многократный GC с паузами
        for (int i = 0; i < 3; i++) {
            System.gc();
            System.runFinalization();
            Thread.sleep(500);
        }

        // Ждем обработки cleaner
        Thread.sleep(3000);
        System.out.println("Test finished");    }
}