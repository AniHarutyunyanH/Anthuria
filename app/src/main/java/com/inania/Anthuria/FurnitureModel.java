package com.inania.Anthuria;

import java.io.File;

public class FurnitureModel {
    private String name;
    private File file;

    public FurnitureModel(String name, File file) {
        this.name = name;
        this.file = file;
    }

    public String getName() { return name; }
    public File getFile() { return file; }
}