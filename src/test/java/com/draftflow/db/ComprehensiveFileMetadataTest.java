package com.draftflow.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveFileMetadataTest {

    @Test
    public void testFileMetadataAccessorsAndJson() {
        FileMetadata meta = new FileMetadata(
                "src/main.java",
                1024L,
                1672531199000L,
                "somehashval123",
                "BLOB",
                100755
        );

        assertEquals("src/main.java", meta.getPath());
        assertEquals(1024L, meta.getSize());
        assertEquals(1672531199000L, meta.getLastModified());
        assertEquals("somehashval123", meta.getHash());
        assertEquals("BLOB", meta.getType());
        assertEquals(100755, meta.getMode());

        // Test JSON serialization
        String json = meta.toJson();
        assertNotNull(json);

        // Test JSON deserialization
        FileMetadata deserialized = FileMetadata.fromJson(json);
        assertNotNull(deserialized);
        assertEquals(meta.getPath(), deserialized.getPath());
        assertEquals(meta.getSize(), deserialized.getSize());
        assertEquals(meta.getLastModified(), deserialized.getLastModified());
        assertEquals(meta.getHash(), deserialized.getHash());
        assertEquals(meta.getType(), deserialized.getType());
        assertEquals(meta.getMode(), deserialized.getMode());
    }

    @Test
    public void testFileMetadataInvalidJson() {
        FileMetadata parsed = FileMetadata.fromJson("{invalid json");
        assertNull(parsed);
    }
}
