package io.github.tryrodave.jsonextended.blockstate;

/** A blockstate file that can't be loaded. The message names the file and the key, ready for one log line. */
public final class BlockstateException extends RuntimeException {
    public BlockstateException(String message) {
        super(message);
    }
}
