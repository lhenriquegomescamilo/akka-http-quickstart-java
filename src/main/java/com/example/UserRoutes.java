package com.example;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import com.example.UserRegistryActor.User;
import com.example.UserRegistryMessages.ActionPerformed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import akka.pattern.Patterns;

/**
 * Routes can be defined in separated classes like shown in here
 */
//#user-routes-class
public class UserRoutes extends AllDirectives {
    //#user-routes-class
    final private ActorRef userRegistryActor;
    final private LoggingAdapter log;


    public UserRoutes(ActorSystem system, ActorRef userRegistryActor) {
        this.userRegistryActor = userRegistryActor;
        log = Logging.getLogger(system, this);
    }

    // Required by the `ask` (?) method below
    Duration timeout = Duration.ofSeconds(5l); // usually we'd obtain the timeout from the system's configuration

    /**
     * This method creates one route (of possibly many more that will be part of your Web App)
     */
    //#all-routes
    //#users-get-delete
    public Route routes() {
        return route(pathPrefix("users", () ->
            route(
                getOrPostUsers(),
                path(PathMatchers.segment(), name -> route(
                    getUser(name),
                    deleteUser(name)
                  )
                )
            )
        ));
    }
    //#all-routes

    //#users-get-delete

    //#users-get-delete
    private Route getUser(String name) {
      return get(() -> {
          // #retrieve-user-info
          CompletionStage<Optional<User>> maybeUser = Patterns
                  .ask(userRegistryActor, new UserRegistryMessages.GetUser(name), timeout)
                  .thenApply(Optional.class::cast);

          return onSuccess(() -> maybeUser,
              performed -> {
                  if (performed.isPresent())
                      return complete(StatusCodes.OK, performed.get(), Jackson.marshaller());
                  else
                      return complete(StatusCodes.NOT_FOUND);
              }
            );
          //#retrieve-user-info
        });
    }

    private Route deleteUser(String name) {
      return
          //#users-delete-logic
          delete(() -> {
            CompletionStage<ActionPerformed> userDeleted = Patterns
              .ask(userRegistryActor, new UserRegistryMessages.DeleteUser(name), timeout)
              .thenApply(ActionPerformed.class::cast);

            return onSuccess(() -> userDeleted,
              performed -> {
                log.info("Deleted user [{}]: {}", name, performed.getDescription());
                return complete(StatusCodes.OK, performed, Jackson.marshaller());
              }
            );
          });
          //#users-delete-logic
    }
    //#users-get-delete

    //#users-get-post
    private Route getOrPostUsers() {
        return pathEnd(() ->
            route(
                get(() -> {
                    CompletionStage<UserRegistryActor.Users> futureUsers = Patterns
                        .ask(userRegistryActor, new UserRegistryMessages.GetUsers(), timeout)
                        .thenApply(UserRegistryActor.Users.class::cast);
                    return onSuccess(() -> futureUsers,
                        users -> complete(StatusCodes.OK, users, Jackson.marshaller()));
                }),
                post(() ->
                    entity(
                        Jackson.unmarshaller(User.class),
                        user -> {
                            CompletionStage<ActionPerformed> userCreated = Patterns
                                .ask(userRegistryActor, new UserRegistryMessages.CreateUser(user), timeout)
                                .thenApply(ActionPerformed.class::cast);
                            return onSuccess(() -> userCreated,
                                performed -> {
                                    log.info("Created user [{}]: {}", user.getName(), performed.getDescription());
                                    return complete(StatusCodes.CREATED, performed, Jackson.marshaller());
                                });
                        }))
            )
        );
    }

    //#users-get-post
}
