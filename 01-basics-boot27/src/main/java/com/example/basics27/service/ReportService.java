package com.example.basics27.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * メソッドレベルの認可（メソッドセキュリティ）の例。
 *
 * URL ベースの認可だけだと「別の Controller から同じサービスを呼んだら素通り」になりがち。
 * 大事な業務ロジックはサービス層でも守ると多層防御になる。
 */
@Service
public class ReportService {

    /**
     * hasRole('ADMIN') は ROLE_ADMIN を持っているかの判定。
     * hasAuthority('ROLE_ADMIN') と書いた場合と等価（hasRole は ROLE_ を補ってくれる）。
     */
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnlyReport() {
        return "管理者向けレポート: 売上 1,234,567 円";
    }

    /** 認証済みなら誰でも。#username に自分の名前しか渡せないようにする例も添える。 */
    @PreAuthorize("isAuthenticated() and #username == authentication.name")
    public String myReport(String username) {
        return username + " さんのレポート";
    }
}
