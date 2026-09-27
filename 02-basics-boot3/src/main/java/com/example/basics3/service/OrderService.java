package com.example.basics3.service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.example.basics3.domain.Order;

/**
 * メソッドセキュリティの代表的な 4 パターンを 1 クラスで見せる。
 *
 * <table border="1">
 *   <tr><th>アノテーション</th><th>いつ評価されるか</th><th>用途</th></tr>
 *   <tr><td>{@code @PreAuthorize}</td><td>メソッド実行<b>前</b></td><td>ROLE / 引数による入口チェック（基本これ）</td></tr>
 *   <tr><td>{@code @PostAuthorize}</td><td>メソッド実行<b>後</b></td><td>戻り値を見てから判定（所有者チェックなど）</td></tr>
 *   <tr><td>{@code @PostFilter}</td><td>メソッド実行後</td><td>戻り値のコレクションから見せてよい要素だけ残す</td></tr>
 *   <tr><td>{@code @PreFilter}</td><td>メソッド実行前</td><td>引数のコレクションを絞る</td></tr>
 * </table>
 *
 * ★注意: @PostAuthorize / @PostFilter は「実行してから捨てる」ので、
 *   更新系メソッドに使うと副作用が起きてしまう（例外で例外的にロールバックされる想定になる）。
 *   参照系に限定して使うこと。
 */
@Service
public class OrderService {

    private final List<Order> orders = new CopyOnWriteArrayList<>(List.of(
            new Order(1, "user", 1000, false),
            new Order(2, "user", 2000, false),
            new Order(3, "manager", 30000, false),
            new Order(4, "admin", 500000, false)
    ));

    /** 全件は ADMIN のみ。 */
    @PreAuthorize("hasRole('ADMIN')")
    public List<Order> findAll() {
        return new ArrayList<>(orders);
    }

    /**
     * 誰でも呼べるが、戻ってくるのは「自分の注文」だけ。
     * filterObject は判定対象の各要素を指す。ADMIN は全部見える。
     */
    @PostFilter("hasRole('ADMIN') or filterObject.owner == authentication.name")
    public List<Order> findVisible() {
        return new ArrayList<>(orders);
    }

    /**
     * returnObject は戻り値。自分の注文か ADMIN なら OK。
     * 該当しなければ AccessDeniedException（＝403）になる。
     */
    @PostAuthorize("hasRole('ADMIN') or returnObject.owner == authentication.name")
    public Order findById(long id) {
        return orders.stream()
                .filter(order -> order.id() == id)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("order not found: " + id));
    }

    /**
     * 承認は MANAGER の仕事。
     * ROLE 階層（ADMIN > MANAGER）を設定してあるので、ADMIN もこのメソッドを呼べる。
     */
    @PreAuthorize("hasRole('MANAGER')")
    public Order approve(long id) {
        Order approved = findByIdInternal(id).approve();
        orders.replaceAll(order -> order.id() == id ? approved : order);
        return approved;
    }

    /** 引数と認証情報を突き合わせる例。他人の名前を渡すと弾かれる。 */
    @PreAuthorize("#owner == authentication.name or hasRole('ADMIN')")
    public List<Order> findByOwner(String owner) {
        return orders.stream().filter(order -> order.owner().equals(owner)).toList();
    }

    /** アノテーションを付けないメソッドは当然ノーチェック（内部利用専用にする）。 */
    private Order findByIdInternal(long id) {
        return orders.stream()
                .filter(order -> order.id() == id)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("order not found: " + id));
    }
}
