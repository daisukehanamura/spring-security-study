package com.example.basics3.web;

import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.basics3.service.OrderService;

@Controller
public class PageController {

    private final OrderService orderService;

    public PageController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/whoami")
    public String whoami(Authentication authentication, Model model) {
        model.addAttribute("name", authentication.getName());
        // ここに表示される権限は「ユーザーが直接持っている権限」だけ。
        // ROLE 階層による上位→下位の展開は、認可判定のときに行われる（一覧には現れない）。
        model.addAttribute("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(", ")));
        model.addAttribute("orders", orderService.findVisible());
        return "whoami";
    }

    /** URL 認可: hasRole("ADMIN") */
    @GetMapping("/admin/dashboard")
    public String admin(Model model) {
        model.addAttribute("title", "管理ダッシュボード (ROLE_ADMIN)");
        model.addAttribute("orders", orderService.findAll());
        return "list";
    }

    /** URL 認可: hasRole("MANAGER")。ROLE 階層により admin もアクセスできる。 */
    @GetMapping("/manager/approvals")
    public String approvals(Model model) {
        model.addAttribute("title", "承認待ち一覧 (ROLE_MANAGER / 階層で ADMIN も可)");
        model.addAttribute("orders", orderService.findVisible());
        return "list";
    }

    /** URL 認可: hasAuthority("report:read")。ROLE ではなく細かい権限での制御。 */
    @GetMapping("/reports/list")
    public String reports(Model model) {
        model.addAttribute("title", "レポート (authority: report:read)");
        model.addAttribute("orders", orderService.findVisible());
        return "list";
    }

    @GetMapping("/403")
    public String forbidden() {
        return "403";
    }
}
