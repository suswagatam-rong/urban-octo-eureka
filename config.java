package com.example.ckyc;

import java.io.FileInputStream;
import java.util.Properties;

public class Config {
    private final Properties p = new Properties();
        public Config(String filename) throws Exception {
                try (FileInputStream fis = new FileInputStream(filename)) { p.load(fis); }
                    }
                        public String get(String k){ return p.getProperty(k); }
                            public String getOrDefault(String k, String def){ return p.getProperty(k, def); }
                            }