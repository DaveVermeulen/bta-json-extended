package io.github.tryrodave.jsonextended.gltf;

/** A glTF or GLB file that can't be read. The message starts with the file id and the key, ready for one log line. */
public final class GltfException extends RuntimeException {
    public GltfException(String message) {
        super(message);
    }
}
