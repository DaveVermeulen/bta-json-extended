package io.github.tryrodave.jsonextended.item;

/** An item model that can't be loaded. The message names the model and the key, ready for one log line. */
public final class ItemModelException extends RuntimeException {
    public ItemModelException(String message) {
        super(message);
    }
}
