package com.ieungsa2

object LinkDatabase {

    // 100% 안전하다고 알려진 도메인 목록 (주로 최상위 도메인 기준)
    val WHITELIST_DOMAINS = setOf(
        // 주요 포털 및 소셜 미디어
        "google.com",
        "youtube.com",
        "facebook.com",
        "instagram.com",
        "twitter.com",
        "naver.com",
        "daum.net",
        "kakao.com",
        // 주요 언론사
        "chosun.com",
        "donga.com",
        "joongang.co.kr",
        "khan.co.kr",
        "hani.co.kr",
        // 주요 쇼핑몰 및 브랜드
        "coupang.com",
        "11st.co.kr",
        "gmarket.co.kr",
        "auction.co.kr",
        "samsung.com",
        "lg.co.kr",
        "apple.com",
        "microsoft.com",
        // 주요 은행
        "nhbank.com",
        "shinhan.com",
        "kbstar.com",
        "wooribank.com",
        "hanabank.com",
        // 공공기관
        "go.kr"
    )

    // 100% 위험하다고 알려진 URL 패턴 또는 도메인
    val BLACKLIST_PATTERNS = setOf(
        // 실제 신고된 피싱 URL 패턴 예시
        ".xyz/login",
        ".top/bank",
        ".buzz/event",
        ".click/verify",
        "info-security-alert.com",
        "secure-update-required.net",
        "web-delivery-service.org"
    )
}
