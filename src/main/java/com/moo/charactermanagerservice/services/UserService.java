package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.User;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class UserService {

    private final RestTemplate restTemplate;

    public UserService() {
        this.restTemplate = new RestTemplate();
    }

    public User getUserDetails(String authorizationHeader) {
        try{
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<User> response = restTemplate.exchange(
                    "http://localhost:8085/api/v1/auth/authorize", HttpMethod.GET, requestEntity, User.class
            );

            return response.getBody();
        }   catch (HttpClientErrorException | HttpServerErrorException e) {
            System.out.println("HTTP Error: " + e.getStatusCode());
            System.out.println("Error Response Body: " + e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Unexpected error occurred", e);
        }
    }

}
