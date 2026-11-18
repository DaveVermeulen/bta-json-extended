package io.github.tryrodave.jsonextended.obj;

import java.util.List;

/**
 * Faces that share an object ({@code o}), group ({@code g}) and material ({@code usemtl}). Each face is a list of
 * corners, each corner zero-based [position, uv or -1, normal or -1].
 *
 * @param material the {@code usemtl} name, or null before the first {@code usemtl}
 */
public record ObjMesh(String object, String group, String material, List<int[][]> faces) {
    /** {@code object/group}, or whichever of the two is set; empty for neither. */
    public String name() {
        if (object.isEmpty())
            return group;
        return group.isEmpty() ? object : object + "/" + group;
    }
}
