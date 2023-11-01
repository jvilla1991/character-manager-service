package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class UserService {

    private final WebClient webClient;

    @Autowired
    private ObjectMapper customObjectMapper;

    @Autowired
    public UserService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://localhost:8085/api/v1/").build();
    }

    public User getUserDetails(String authorizationHeader) {
        User user = webClient.get()
                .uri("auth/authorize")
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(User.class)
                .block();
        return user;
    }

}
