package com.example.basics27.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * curl で挙動を確かめるための API。
 *
 * <pre>
 * curl -u user:password  http://localhost:8080/api/me
 * curl -u user:password  http://localhost:8080/api/admin/stats   -> 403
 * curl -u admin:password http://localhost:8080/api/admin/stats   -> 200
 * curl                   http://localhost:8080/api/me            -> 401
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", authentication.getName());
        body.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        return body;
    }

    @GetMapping("/admin/stats")
    public Map<String, Object> stats() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("activeUsers", 42);
        body.put("note", "ROLE_ADMIN だけが見られる情報");
        return body;
    }
}
