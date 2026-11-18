package io.github.tryrodave.jsonextended.obj;

/** An OBJ or MTL file that can't be read. The message starts with the file id and line, ready for one log line. */
public final class ObjException extends RuntimeException {
    public ObjException(String message) {
        super(message);
    }

    static ObjException at(String id, int line, String message) {
        return new ObjException(id + " line " + line + ": " + message);
    }
}
