package com.bradandmarsha.acruet.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaSettingsTest {

    @Test
    void tileImageUrlBuildsHttpsPath() {
        MediaSettings settings = new MediaSettings("media.example.com");
        assertEquals(
                "https://media.example.com/media/acruet-bw.jpg",
                settings.tileImageUrl("/media/acruet-bw.jpg"));
        assertEquals(
                "https://media.example.com/media/acruet-bw.jpg",
                settings.tileImageUrl("acruet-bw.jpg"));
    }
}
