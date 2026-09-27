package com.example.basics27;

import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.ActiveProfiles;

/**
 * WebSecurityConfigurerAdapter 方式でも、まったく同じ認可ルールが成立することを確認する。
 * ＝ 2.7 のうちに書き換えても振る舞いは変わらない（＝安心して 3.x へ移行できる）という確認。
 */
@DisplayName("WebSecurityConfigurerAdapter 方式(非推奨)")
@ActiveProfiles("adapter")
class LegacyAdapterSecurityConfigTest extends AbstractSecurityRulesTest {
}
