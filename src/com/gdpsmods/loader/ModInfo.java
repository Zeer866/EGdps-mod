package com.gdpsmods.loader;

import java.io.File;

public class ModInfo {
    public String id, name = "", version = "?", author = "?", description = "", main, type, error;
    public File file;
    public boolean enabled = true, loaded = false;
}
