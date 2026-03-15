package org.example;

public interface Line {
    Cell getCell(int visualCol);

    int cellLength();

    int visualLength();

    String getString();

    boolean isWrapped();
}
