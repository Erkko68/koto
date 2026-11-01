package io.github.erkko68.koto.desktop;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Extraction-cache and cleanup behavior of {@link KotoLoader} (no native loading). */
public class KotoLoaderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static Supplier<InputStream> content(String s) {
        return () -> new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void extractsOnceAndReuses() throws Exception {
        File cacheRoot = tmp.newFolder("cache");
        File libDir = new File(cacheRoot, "koto-abc123");

        File lib = KotoLoader.extractIfNeeded(cacheRoot, libDir, "libkoto.dylib", content("v1"));
        assertTrue(lib.isFile());
        assertEquals("v1", Files.readString(lib.toPath()));

        // Second call must not re-extract — new content is ignored because the file exists.
        File again = KotoLoader.extractIfNeeded(cacheRoot, libDir, "libkoto.dylib", content("v2"));
        assertEquals(lib, again);
        assertEquals("v1", Files.readString(again.toPath()));
    }

    @Test
    public void noPartialFileLeftBehind() throws Exception {
        File cacheRoot = tmp.newFolder("cache");
        File libDir = new File(cacheRoot, "koto-abc123");
        KotoLoader.extractIfNeeded(cacheRoot, libDir, "lib.so", content("data"));

        // Only the lib itself remains; the temp file was atomically moved away.
        String[] files = libDir.list();
        assertEquals(1, files == null ? 0 : files.length);
        assertEquals("lib.so", files[0]);
    }

    @Test
    public void concurrentExtractionYieldsOneConsistentFile() throws Exception {
        File cacheRoot = tmp.newFolder("cache");
        File libDir = new File(cacheRoot, "koto-abc123");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = new java.util.ArrayList<Future<File>>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return KotoLoader.extractIfNeeded(cacheRoot, libDir, "lib.so", content("payload"));
                }));
            }
            start.countDown();
            for (Future<File> f : futures) {
                assertEquals("payload", Files.readString(f.get(30, TimeUnit.SECONDS).toPath()));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void cleanupPurgesOldDirsKeepsRecentAndCurrent() throws Exception {
        File cacheRoot = tmp.newFolder("cache");
        File current = mkLibDir(cacheRoot, "koto-current", 40);
        File stale = mkLibDir(cacheRoot, "koto-stale", 40);
        File recent = mkLibDir(cacheRoot, "koto-recent", 1);
        File unrelated = mkLibDir(cacheRoot, "other-dir", 40);

        KotoLoader.cleanupStaleCaches(cacheRoot, current);

        assertTrue("current build's dir must survive", current.isDirectory());
        assertFalse("40-day-old dir must be purged", stale.exists());
        assertTrue("recently used dir must survive", recent.isDirectory());
        assertTrue("non-cache dirs must be untouched", unrelated.isDirectory());
    }

    @Test
    public void cleanupDisabledByProperty() throws Exception {
        File cacheRoot = tmp.newFolder("cache");
        File current = mkLibDir(cacheRoot, "koto-current", 40);
        File stale = mkLibDir(cacheRoot, "koto-stale", 40);
        System.setProperty("koto.data.cleanup.days", "0");
        try {
            KotoLoader.cleanupStaleCaches(cacheRoot, current);
            assertTrue(stale.isDirectory());
        } finally {
            System.clearProperty("koto.data.cleanup.days");
        }
    }

    private static File mkLibDir(File root, String name, int ageDays) throws Exception {
        File dir = new File(root, name);
        assertTrue(dir.mkdirs());
        Files.writeString(new File(dir, "lib.so").toPath(), "x");
        assertTrue(dir.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ageDays)));
        return dir;
    }
}
