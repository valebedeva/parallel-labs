package laba5;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Query;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.Timeout;
import java.time.Duration;
import javafx.util.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class TimeRequestTester {
    private static  final Duration TIMEOUT = Duration.ofSeconds(2);

    public static void main(String[] args) throws IOException {
        System.out.println("start!");
        ActorSystem system = ActorSystem.create("routes");
        final Http http = Http.get(system);
        ActorRef ActorCashing = system.actorOf(Props.create(CashingActor.class));
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = createFlow(materializer, ActorCashing);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                routeFlow,
                ConnectHttp.toHost("localhost", 8080),
                materializer
        );
        System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read();
        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> system.terminate()); // and shutdown when done
    }

    private static Flow<HttpRequest, HttpResponse, NotUsed> createFlow(ActorMaterializer materializer, ActorRef actorCashing) {
        return Flow.of(HttpRequest.class)
                .map((r) -> {
                    Query q = r.getUri().query();
                    String testUrl  = q.get("testUrl").get();
                    int count = Integer.parseInt(q.get("count").get());
                    System.out.println(count);
                    return new Pair<>(testUrl, count);
                })
                .mapAsync(1, (Pair<String, Integer> pair) -> {
                    CompletionStage<Object> stage = Patterns.ask(actorCashing, new MessageGetResult(pair.getKey()), TIMEOUT);
                    return stage.thenCompose((Object time) -> {
                        if ((float) time >= 0) {
                            return CompletableFuture.completedFuture(new Pair<>(pair.getKey(), (float)time));
                        }
                        Flow<Pair<String, Integer>, Long, NotUsed> flow =
                                Flow.<Pair<String, Integer>>create()
                                        .mapConcat(p -> {
                                            return new ArrayList<>(Collections.nCopies(p.getValue(), p.getKey()));
                                        })
                                        .mapAsync(pair.getValue(), (String url) -> {
                                            long start = System.currentTimeMillis();
                                            asyncHttpClient().prepareGet(url).execute();
                                            long end = System.currentTimeMillis();
                                            long finalTime = end - start;
                                            return CompletableFuture.completedFuture(finalTime);
                                        });
                        return Source.from(Collections.singletonList(pair))
                                .via(flow)
                                .toMat(Sink.fold(0L, Long::sum), Keep.right())
                                .run(materializer)
                                .thenApply(totalSum -> {
                                    return new Pair<>(pair.getKey(), totalSum / pair.getValue());
                                });
                    });
                })
                .map((r) -> {
                    actorCashing.tell(new MessageTest(r.getValue(), r.getKey()), ActorRef.noSender());
                    return HttpResponse.create().withEntity(r.getValue().toString() + "\n");
                });
    }

}
