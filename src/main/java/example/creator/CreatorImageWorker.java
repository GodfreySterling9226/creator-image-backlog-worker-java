package example.creator;

import java.io.IOException;

public final class CreatorImageWorker {
    private final InfraiClient client;

    public CreatorImageWorker(InfraiClient client) { this.client = client; }

    public Delivery process(Job job) throws IOException, InterruptedException {
        validate(job);
        String result = client.resize(job.image(), job.width(), job.height(), job.clientId());
        if (!result.contains("\"ok\":true")) throw new IOException("image resize was not accepted");
        return new Delivery(job.subscriber(), job.image(), "DELIVERED");
    }

    public static void validate(Job job) {
        if (job.image().isBlank() || job.width() <= 0 || job.height() <= 0) {
            throw new IllegalArgumentException("image and positive dimensions are required");
        }
    }

    public record Job(String clientId, String subscriber, String image, int width, int height) {}
    public record Delivery(String subscriber, String image, String state) {}
}
