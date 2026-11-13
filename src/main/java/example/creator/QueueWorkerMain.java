package example.creator;

public final class QueueWorkerMain {
    private QueueWorkerMain() {}

    public static void main(String[] args) throws Exception {
        String key = System.getenv("INFRAI_API_KEY");
        InfraiClient client = new InfraiClient(key);
        String queue = args.length == 0 ? "creator-image-delivery" : args[0];
        String response = client.consume(queue);
        System.out.println("QUEUE_RESPONSE " + response);
        System.out.println("READY: pass the consumed payload to CreatorImageWorker.process(Job) and acknowledge after DELIVERED.");
    }
}
