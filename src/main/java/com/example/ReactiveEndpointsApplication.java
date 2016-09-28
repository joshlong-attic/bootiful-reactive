package com.example;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.HttpServer;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.RequestPredicates.GET;
import static org.springframework.web.reactive.function.RequestPredicates.accept;
import static org.springframework.web.reactive.function.RouterFunctions.route;

@SpringBootApplication
public class ReactiveEndpointsApplication {

    @Bean
    CommandLineRunner commandLineRunner(PersonRepository repository) {
        return args -> Stream.of("Stephane Maldini", "Arjen Poutsma")
                .forEach(name -> repository.save(new Person(name, new Random().nextInt(100))));
    }

    @Bean
    RouterFunction<?> routingFunction(PersonHandler handler) {
        return route(GET("/person/{id}"), handler::getPerson)
                .and(route(GET("/person").and(accept(APPLICATION_JSON)), handler::listPeople));
    }

    @Bean
    HttpServer server(@Value("${server.host:localhost}") String host,
                      @Value("${server.port:8080}") int port,
                      RouterFunction<?> router) {
        HttpHandler httpHandler = RouterFunctions.toHttpHandler(router);
        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
        HttpServer httpServer = HttpServer.create(host, port);
        httpServer.start(adapter);
        return httpServer;
    }

    public static void main(String[] args) {
        SpringApplication.run(ReactiveEndpointsApplication.class, args);
    }
}


@Entity
class Person {

    @Id
    @GeneratedValue
    private Long id;

    private String familyName;

    private int age;

    Person(@JsonProperty("familyName") String familyName, @JsonProperty("age") int age) {
        this.familyName = familyName;
        this.age = age;
    }

    Person() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return familyName;
    }

    public int getAge() {
        return age;
    }
}

interface PersonRepository extends JpaRepository<Person, Long> {

    @Query("select p from com.example.Person p ")
    Stream<Person> all();

    CompletableFuture<Person> findById(Long id);

}

@Component
class PersonHandler {

    private final PersonRepository repository;

    public PersonHandler(PersonRepository repository) {
        this.repository = repository;
    }

    Response<Publisher<Person>> getPerson(Request request) {
        Mono<Person> person = Mono.justOrEmpty(request.pathVariable("id"))
                .map(Long::valueOf)
                .then(id -> Mono.fromFuture(this.repository.findById(id)));
        return Response.ok().body(BodyInserters.fromPublisher(person, Person.class));
    }

    Response<Publisher<Person>> listPeople(Request request) {
        Flux<Person> people = Flux.fromStream(this.repository.all());
        return Response.ok().body(BodyInserters.fromPublisher(people, Person.class));
    }

}
