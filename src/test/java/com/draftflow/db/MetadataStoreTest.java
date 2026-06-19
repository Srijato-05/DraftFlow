package com.draftflow.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MetadataStoreTest {

    @TempDir
    Path tempDir;

    private MetadataStore db;

    @BeforeEach
    public void setUp() throws IOException {
        Path dbPath = tempDir.resolve("index.mv.db");
        db = new MetadataStore(dbPath);
        db.open();
    }

    @AfterEach
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void testFileMetadataPersistence() {
        FileMetadata meta = new FileMetadata(
            "src/main/java/CAS.java",
            2048L,
            System.currentTimeMillis(),
            "dummyHashSHA256",
            "BLOB",
            100644
        );

        db.putFile(meta);
        db.commit();

        FileMetadata retrieved = db.getFile("src/main/java/CAS.java");
        assertNotNull(retrieved);
        assertEquals(meta.getPath(), retrieved.getPath());
        assertEquals(meta.getSize(), retrieved.getSize());
        assertEquals(meta.getHash(), retrieved.getHash());
        assertEquals(meta.getType(), retrieved.getType());
        assertEquals(meta.getMode(), retrieved.getMode());
    }

    @Test
    public void testRefOperations() {
        String mainHash = "mainRevisionHash123";
        db.setRef("heads/main", mainHash);
        
        String featureHash = "featureRevisionHash456";
        db.setRef("heads/feature", featureHash);
        db.commit();

        // Check values
        assertEquals(mainHash, db.getRef("heads/main"));
        assertEquals(featureHash, db.getRef("heads/feature"));

        List<String> refs = db.getRefNames();
        assertEquals(2, refs.size());
        assertTrue(refs.contains("heads/main"));
        assertTrue(refs.contains("heads/feature"));

        db.removeRef("heads/feature");
        db.commit();

        assertNull(db.getRef("heads/feature"));
        assertEquals(1, db.getRefNames().size());
    }

    @Test
    public void testConfigOperations() {
        db.setConfig("activeHead", "heads/main");
        db.setConfig("activeChangeId", "uuid-123456");
        db.commit();

        assertEquals("heads/main", db.getConfig("activeHead"));
        assertEquals("uuid-123456", db.getConfig("activeChangeId"));
    }

    @Test
    public void testChangeHistory() {
        String changeId = "change-uuid-789";
        
        db.setChangeRevision(changeId, "rev1");
        db.setChangeRevision(changeId, "rev2");
        db.setChangeRevision(changeId, "rev3");
        // Test duplicate prevention
        db.setChangeRevision(changeId, "rev3");
        db.commit();

        assertEquals("rev3", db.getChangeRevision(changeId));
        
        List<String> history = db.getChangeHistory(changeId);
        assertEquals(3, history.size());
        assertEquals("rev1", history.get(0));
        assertEquals("rev2", history.get(1));
        assertEquals("rev3", history.get(2));
    }
}
