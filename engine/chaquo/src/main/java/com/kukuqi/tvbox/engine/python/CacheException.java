package com.kukuqi.tvbox.engine.python;

import com.chaquo.python.PyObject;

public final class CacheException extends RuntimeException {

    private final PyObject error;

    public CacheException(PyObject error) {
        super("Python cache loader failed");
        this.error = error;
    }

    public PyObject error() {
        return error;
    }
}
