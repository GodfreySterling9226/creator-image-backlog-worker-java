package example.creator;

public final class CreatorImageWorkerTest {
    public static void main(String[] args) throws Exception {
        CreatorImageWorker.Job ready = new CreatorImageWorker.Job("job-17", "sub-4", "img-9", 1200, 800);
        CreatorImageWorker.validate(ready);
        require(ready.width() == 1200 && ready.height() == 800, "ready image should keep delivery dimensions");
        boolean rejected = false;
        try { CreatorImageWorker.validate(new CreatorImageWorker.Job("job-18", "sub-4", "", 1200, 800)); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "missing image should be rejected before transport");
        System.out.println("PASS business decision");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
