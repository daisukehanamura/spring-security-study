package com.example.basics3.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.basics3.domain.Order;
import com.example.basics3.service.OrderService;

/**
 * /api/** は apiFilterChain（ステートレス + Basic 認証）が担当する。
 *
 * <pre>
 * curl localhost:8081/api/public/ping                        # 認証不要
 * curl -u user:password    localhost:8081/api/orders         # GET は USER でも OK
 * curl -u user:password -X POST localhost:8081/api/orders    # POST は ADMIN のみ -> 403
 * curl -u admin:password -X POST localhost:8081/api/orders   # -> 200
 * curl -u user:password    localhost:8081/api/orders/4       # 他人の注文 -> 403 (@PostAuthorize)
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class OrderApiController {

    private final OrderService orderService;

    public OrderApiController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/public/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", authentication.getName());
        body.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        return body;
    }

    /** @PostFilter により、自分の注文だけが返る（ADMIN は全件）。 */
    @GetMapping("/orders")
    public List<Order> orders() {
        return orderService.findVisible();
    }

    /** @PostAuthorize により、他人の注文を指定すると 403。 */
    @GetMapping("/orders/{id}")
    public Order order(@PathVariable long id) {
        return orderService.findById(id);
    }

    /** URL 認可（HttpMethod.POST + hasRole("ADMIN")）で守られている。 */
    @PostMapping("/orders")
    public Map<String, String> create() {
        return Map.of("result", "created");
    }

    @GetMapping("/admin/stats")
    public Map<String, Object> stats() {
        return Map.of("orders", orderService.findAll().size());
    }
}
