package com.belgee.radionamedb;

/**
 * Модель одной радиостанции.
 */
public class Station {
    public String freq;       // "103.4" для FM, "675" для AM
    public String name;       // "Русское Радио"
    public boolean isAm;      // FM/AM

    public Station(String freq, String name, boolean isAm) {
        this.freq = freq;
        this.name = name;
        this.isAm = isAm;
    }

    public String displayFreq() {
        if (isAm) {
            return freq + " кГц";
        }
        return freq + " МГц";
    }
}
