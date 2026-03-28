package com.pkv.chat.service;

import java.util.List;

public record HydeResult(String ko, String en) {

    public List<String> documents() {
        return List.of(ko, en);
    }
}
