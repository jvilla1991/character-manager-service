package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.User;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class UserService {

    private final RestTemplate restTemplate;

    public UserService() {
        this.restTemplate = new RestTemplate();
    }

    public User getUserDetails(String authorizationHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<User> response = restTemplate.exchange(
                "http://localhost:8085/api/v1/auth/authorize", HttpMethod.GET, requestEntity, User.class
        );

        return response.getBody();
    }

}
