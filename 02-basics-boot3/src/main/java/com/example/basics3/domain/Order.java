package com.example.basics3.domain;

/** 学習用の注文データ。owner は「その注文を見てよいユーザー名」。 */
public record Order(long id, String owner, int amount, boolean approved) {

    public Order approve() {
        return new Order(id, owner, amount, true);
    }
}
