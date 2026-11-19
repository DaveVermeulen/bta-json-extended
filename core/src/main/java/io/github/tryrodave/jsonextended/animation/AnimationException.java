package io.github.tryrodave.jsonextended.animation;

/** An animation file that can't be read, with the file id and where in it. */
public class AnimationException extends RuntimeException {
    public AnimationException(String message) {
        super(message);
    }
}
