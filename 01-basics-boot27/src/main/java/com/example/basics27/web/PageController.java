package com.example.basics27.web;

import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.basics27.service.ReportService;

/** 画面(Thymeleaf)側のコントローラ。 */
@Controller
public class PageController {

    private final ReportService reportService;

    public PageController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 誰でも見られるトップページ */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /** 自前のログイン画面（Security の loginPage(...) で指定したパス） */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * ログインすると誰が何の権限で入っているかが見える画面。
     * Authentication は引数で受け取れる（SecurityContextHolder から自分で取る必要はない）。
     */
    @GetMapping("/whoami")
    public String whoami(Authentication authentication, Model model) {
        model.addAttribute("name", authentication.getName());
        model.addAttribute("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(", ")));
        model.addAttribute("authClass", authentication.getClass().getSimpleName());
        return "whoami";
    }

    /** URL ベースの認可: /admin/** は hasRole("ADMIN") が必要 */
    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        model.addAttribute("report", reportService.adminOnlyReport());
        return "admin";
    }

    /**
     * URL は誰でも(認証済みなら)アクセスできるが、
     * 呼び出す先のサービスメソッドが @PreAuthorize で守られている例。
     * user でアクセスすると 403 になる = メソッドセキュリティが効いている証拠。
     */
    @GetMapping("/report/admin-only")
    public String reportViaMethodSecurity(Model model) {
        model.addAttribute("report", reportService.adminOnlyReport());
        return "admin";
    }

    /** アクセス拒否時の画面（exceptionHandling().accessDeniedPage("/403")） */
    @GetMapping("/403")
    public String forbidden() {
        return "403";
    }
}
