package com.asifqureshi.aivoicestudio;

import static org.junit.Assert.assertEquals;

import com.asifqureshi.aivoicestudio.utils.Format;

import org.junit.Test;

public class FormatTest {
    @Test public void durations() {
        assertEquals("0:00", Format.duration(0));
        assertEquals("0:09", Format.duration(9_400));
        assertEquals("1:23", Format.duration(83_000));
        assertEquals("1:02:03", Format.duration(3_723_000));
        assertEquals("0:00", Format.duration(-5));
    }

    @Test public void fileSizes() {
        assertEquals("512 B", Format.fileSize(512));
        assertEquals("1.5 KB", Format.fileSize(1536));
        assertEquals("2.00 MB", Format.fileSize(2L * 1024 * 1024));
    }

    @Test public void initialsAndExtensions() {
        assertEquals("AQ", Format.initials("Asif Qureshi"));
        assertEquals("A", Format.initials("asif"));
        assertEquals("?", Format.initials("  "));
        assertEquals("opus", Format.extensionFor("audio/ogg"));
        assertEquals("mp3", Format.extensionFor("audio/mpeg"));
        assertEquals("mp3", Format.extensionFor(null));
    }
}
