package io.zonky.test.db.provider.support;

import io.zonky.test.db.provider.EmbeddedDatabase;

public abstract class AbstractEmbeddedDatabase extends AbstractDelegatingDataSource implements EmbeddedDatabase {

    private final Runnable closeCallback;

    protected AbstractEmbeddedDatabase(Runnable closeCallback) {
        this.closeCallback = closeCallback;
    }

    @Override
    public synchronized void close() {
        closeCallback.run();
    }
}
