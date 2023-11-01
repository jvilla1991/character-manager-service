package com.moo.charactermanagerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CharacterManagerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CharacterManagerServiceApplication.class, args);
    }

//    @EventListener(ApplicationReadyEvent.class)
//    public void insertMockData() {
//        User user1 = User.Builder.newInstance()
//                .setUuid(UUID.fromString("e8c7cf3b-b801-46a2-9b5b-45039b0cef6b"))
//                .setName("Jeremy")
//                .setEmail("cawkpot@gmail.com")
//                .setPassword("password1").build();
//        User user2 = User.Builder.newInstance()
//                .setUuid(UUID.fromString("6cdb5672-e58e-4ef8-bac1-7e8703971c90"))
//                .setName("Kendrix")
//                .setEmail("ckendrixlauzon@gmail.com")
//                .setPassword("password2").build();
//        User user3 = User.Builder.newInstance()
//                .setUuid(UUID.fromString("5c5417e8-5b0a-4c5d-bd7f-2f28f6aad8ca"))
//                .setName("Chris")
//                .setEmail("azballkid20@aim.com")
//                .setPassword("password3").build();
//
//        userService.addUser(user1);
//        userService.addUser(user2);
//        userService.addUser(user3);
//
//        pcService.addPC(PC.Builder.newInstance()
//                .setClazz("Fighter")
//                .setName("Tibbles")
//                .setLevel((short) 1)
//                .setUser(user1)
//                .setPlayerName(user1.getName())
//                .build());
//        pcService.addPC(PC.Builder.newInstance()
//                .setClazz("Warlock")
//                .setName("Ozwaroth")
//                .setLevel((short) 2)
//                .setUser(user2)
//                .setPlayerName(user2.getName())
//                .build());
//        pcService.addPC(PC.Builder.newInstance().setClazz("Paldain").setName("Taydok").setLevel((short) 3).setUser(user2).setPlayerName(user2.getName()).build());
//        pcService.addPC(PC.Builder.newInstance().setClazz("Rogue").setName("Dexon").setLevel((short) 2).setUser(user3).setPlayerName(user3.getName()).build());
//    }

}
