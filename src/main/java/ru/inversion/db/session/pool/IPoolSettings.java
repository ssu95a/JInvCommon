package ru.inversion.db.session.pool;

public interface IPoolSettings {

    int getPoolMaxActiveConnections();
    int getPoolMaxIdleConnections();
    int getPoolTimeToWait(); // ms
    boolean isPoolWaitWithCoef();
    int getPoolNumberOfAttempts();

    /** Если пул превышен, создавать реальное соединение вне пула */
    default boolean isCreateRealIfExceeded() { return false; }

    /**дефолтные настройки  */
    static IPoolSettings defaultSettings() {
        return new IPoolSettings() {
            @Override public int getPoolMaxActiveConnections() { return 5; }
            @Override public int getPoolMaxIdleConnections() { return 2; }
            @Override public int getPoolTimeToWait() { return 1000; }
            @Override public boolean isPoolWaitWithCoef() { return true; }
            @Override public int getPoolNumberOfAttempts() { return 10; }
        };
    }
}
