# Nginx / Cloudflare / 도메인 트러블슈팅

---

### 1. IP 주소로 직접 Swagger에 접속하던 문제

**증상**
```
http://<SERVER-IP>:8080/swagger-ui/index.html
```

**해결**
1. 도메인 구매
2. Cloudflare에 도메인 등록
3. `api.<도메인>` 서브도메인을 서버 공인 IP에 연결
4. Nginx로 `80 → 8080` 프록시 구조 적용

---

### 2. Cloudflare에 잘못된 DNS 레코드가 자동 등록된 문제

**원인**
Cloudflare가 기존 DNS 정보를 자동 스캔하면서 현재 프로젝트와 무관한 레코드를 가져옴.

**해결**
자동 감지된 레코드 삭제 후 실제 서버 IP로 재등록.
```
A   api   <SERVER-IP>
A   @     <SERVER-IP>
```

---

### 3. Cloudflare 네임서버 Pending 상태 유지

**원인**
호스팅 도메인 관리 페이지에서 네임서버가 아직 Cloudflare로 변경되지 않았음.

**해결**
호스팅 관리 페이지에서 네임서버 2개 변경 후 전파 완료까지 대기.

---

### 4. api.<도메인>:8080은 접속되지만 api.<도메인>는 동작하지 않던 문제

**원인**
80 포트에서 8080으로 넘겨주는 리버스 프록시 구성이 없었음.

**해결**
```nginx
server {
    listen 80;
    server_name api.<도메인>;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

### 5. Nginx 설정 후에도 기본 페이지가 열리던 문제

**원인**
Nginx 기본 사이트(`default`)가 여전히 활성화되어 있었음.

**해결**
```bash
sudo rm /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

---

### 6. Nginx는 정상인데 루트 경로(/)에서 403 발생

**원인**
루트 경로(`/`)는 Spring Security에서 보호 또는 미구현 상태.

**해결**
루트 경로 대신 실제 Swagger 경로로 테스트.
```bash
curl -I -H "Host: api.<도메인>" http://127.0.0.1/swagger-ui/index.html
```

---

### 7. Cloudflare 521 Web server is down

**원인**
Cloudflare에서 원본 서버(Origin)로의 연결 실패.

**해결**
```bash
sudo ufw status
sudo ss -ltnp | grep ':80'
```
`api` 레코드를 `Proxied → DNS only`로 변경하여 원본 서버 자체 정상 여부 확인.

---

### 8. 외부 접속 시 Swagger 정적 리소스 로딩이 매우 느린 문제

**원인**
Cloudflare, 포트포워딩, 외부망, 브라우저 다중 정적 파일 요청이 얽히면서 지연 발생.

**해결**
- `api` 레코드를 `DNS only` 상태로 두고 원인 분리
- 외부 노출 포트를 `80`, `443` 중심으로 단순화

---

### 9. HTTP 포트에서 HTTPS 요청이 들어와 Tomcat이 파싱 실패

**증상**
```
Invalid character found in method name [0x16 0x03 0x01 ...]
```

**원인**
HTTP만 받는 8080 포트에 HTTPS(TLS) 요청이 직접 들어옴.

**해결**
공유기 포트포워딩에서 `8080 → 8080` 규칙 제거.

---

### 10. Nginx에 443 SSL 설정을 넣었지만 인증서 파일 없어 실패

**증상**
```
cannot load certificate "/etc/ssl/certs/cloudflare-origin.pem"
```

**해결**
443 서버 블록 우선 제거 → HTTP 구성 복구 후 Cloudflare Origin Certificate 발급 후 적용.
