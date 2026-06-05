package com.satya.urlshortener;

import java.time.ZoneId;
import java.util.TimeZone;

public class TestTimezone {
    public static void main(String[] args) {
        System.out.println("ZoneId = " + ZoneId.systemDefault());
        System.out.println("TimeZone = " + TimeZone.getDefault().getID());
    }
}