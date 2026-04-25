# 트러블슈팅 기록

---

## Flyway

### 1. V4 마이그레이션 SQL 구문 오류 (백틱)

**증상**
```
ERROR: syntax error at or near "``"
Position: 1
Location: db/migration/V4__add_member_status.sql, Line: 3
```

**원인**
`V4__add_member_status.sql` 파일 끝에 백틱(`` ` ``)이 2개 포함되어 있었음.
PostgreSQL은 MySQL 방식의 백틱을 지원하지 않음.

**해결**
3번째 줄 백틱 제거. 파일은 2줄만 있어야 함.
```sql
ALTER TABLE member
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
```

---

### 2. V4 Checksum 불일치

**증상**
```
Migration checksum mismatch for migration version 4
-> Applied to database : -1119497084
-> Resolved locally    : -1211633977
```

**원인**
V4 SQL 파일을 DB에 적용한 뒤 파일 내용을 수정함.
Flyway는 이미 적용된 마이그레이션 파일이 변경되면 체크섬 불일치로 실행 거부.

**해결**
개발 환경이므로 DB 초기화로 해결.
```bash
docker compose down -v && docker compose up -d
```
운영 환경이라면 `flywayRepair` 사용.

---

### 3. V5 PK 컬럼명 불일치

**증상**
```
Schema-validation: missing column [attendance_id] in table [attendance]
```

**원인**
V5 SQL에서 PK를 `id`로 작성했으나 엔티티는 `@Column(name = "attendance_id")`로 매핑.

**해결**
V5 SQL PK 컬럼명을 엔티티와 일치시킴.
```sql
attendance_id BIGSERIAL PRIMARY KEY
```

---

### 4. V5 외래키 참조 컬럼 오류

**증상**
```
ERROR: column "id" referenced in foreign key constraint does not exist
```

**원인**
`member` 테이블 PK가 `member_id`인데 `REFERENCES member(id)`로 잘못 참조.

**해결**
```sql
-- Before
member_id BIGINT NOT NULL REFERENCES member(id)

-- After
member_id BIGINT NOT NULL REFERENCES member(member_id)
```

---

## JWT

### 1. @Value 잘못된 import

**증상**
`jwt.secret` 값이 주입되지 않아 NullPointerException 발생.

**원인**
`@Value`를 `lombok.Value`로 import함.

**해결**
```java
// Before
import lombok.Value;

// After
import org.springframework.beans.factory.annotation.Value;
```

---

### 2. 환경변수 로딩 실패

**증상**
서버 실행 시 `jwt.secret` 값이 로딩되지 않음.

**원인**
`application-local.properties`에서 키를 `JWT_SECRET`(대문자)으로 작성했으나
`application.properties`는 `jwt.secret`(소문자)으로 바인딩.

**해결**
키 이름을 통일하거나 환경변수 치환 방식 사용.
```properties
jwt.secret=${JWT_SECRET}
```

---

## GitHub Actions

### 1. CI 워크플로우가 실행되지 않음

**증상**
`.yml` 파일 커밋 후 푸시해도 GitHub Actions가 실행되지 않음.

**원인**
워크플로우 파일 위치가 잘못됨.
```
잘못된 위치: .github/ci.yml
올바른 위치: .github/workflows/ci.yml
```

**해결**
```bash
mkdir -p .github/workflows
mv .github/ci.yml .github/workflows/ci.yml
```

---

### 2. CI에서 WeakKeyException으로 테스트 실패

**증상**
```
Caused by: io.jsonwebtoken.security.WeakKeyException
AttendanceApplicationTests > contextLoads() FAILED
```

**원인**
jjwt는 최소 32자 이상의 시크릿 키를 요구하는데 GitHub Secrets에 짧은 값이 설정되어 있었음.

**해결**
`src/test/resources/application.properties`에 테스트 전용 키 지정.
```properties
jwt.secret=test-secret-key-for-ci-must-be-at-least-32-characters-long
```
테스트 환경은 외부 환경변수에 의존하지 않도록 독립적으로 유지.

## Hyper-V / Ubuntu Server 설치

### 1. Ubuntu Desktop ISO로 설치 시 검은 화면에서 멈춤

#### 증상
- Hyper-V에서 Ubuntu VM을 생성하고 부팅하면 GRUB 메뉴는 정상적으로 표시됨
- `Try or Install Ubuntu` 선택 후 진행하면 검은 화면과 `X` 모양 커서만 보이고 설치가 진행되지 않음

#### 원인
- 설치 ISO가 `Ubuntu Desktop` 버전이었음
- Hyper-V 환경에서 Ubuntu Desktop GUI 설치가 그래픽 초기화 문제로 멈추는 경우가 있음
- 서버 구축 목적에도 Desktop보다 Server ISO가 더 적합함

#### 해결
`desktop` ISO 대신 `server` ISO로 교체함

```text
Before: ubuntu-24.04.4-desktop-amd64.iso
After : ubuntu-24.04.4-live-server-amd64.iso
```

#### 정리
- Hyper-V에서 서버 용도로 Ubuntu를 설치할 때는 Desktop보다 Server ISO를 사용하는 것이 안정적임
- 포트폴리오 및 실무 유사 환경 측면에서도 Ubuntu Server가 더 적절함

---

### 2. Hyper-V에서 VM 부팅은 되지만 설치 화면 진입에 실패함

#### 증상
- VM 실행 후 검은 화면만 보이거나 설치 화면으로 정상 진입하지 못함

#### 원인
다음 중 하나일 가능성이 높았음

- DVD 드라이브에 ISO가 제대로 연결되지 않음
- Secure Boot 설정이 Ubuntu와 맞지 않음
- 부팅 우선순위에서 DVD가 앞에 오지 않음

#### 해결
아래 항목들을 순서대로 점검함

1. 설정 → SCSI 컨트롤러 → DVD 드라이브에서 ISO 파일이 연결되어 있는지 확인
2. 설정 → 보안에서 Secure Boot 템플릿을 Ubuntu와 호환되는 값으로 조정
3. 설정 → 펌웨어에서 DVD Drive를 부팅 우선순위 상단으로 배치

#### 정리
Hyper-V에서 Ubuntu 설치가 되지 않을 때는 VM 자체를 다시 만들기 전에 다음 설정을 먼저 확인하는 것이 효율적임

- ISO 연결 상태
- Secure Boot 설정
- Firmware 부팅 순서

---

### 3. Secure Boot 설정으로 인해 Ubuntu 부팅이 불안정할 수 있음

#### 증상
- ISO는 연결되어 있고 GRUB 또는 검은 화면까지는 진입하지만 정상 설치 화면 진입이 불안정함

#### 원인
- Hyper-V의 기본 Secure Boot 설정은 Windows 중심이라 Ubuntu와 궁합이 맞지 않을 수 있음

#### 해결
- 설정 → 보안에서 Secure Boot 템플릿을 `Microsoft UEFI Certificate Authority`로 변경함
- 필요 시 Secure Boot를 일시적으로 비활성화하여 원인을 분리함

#### 정리
- Ubuntu VM은 Hyper-V 기본 Secure Boot 설정 그대로 두기보다 Linux 호환 템플릿을 확인하는 것이 좋음

---

### 4. 외부 가상 스위치에 잘못된 NIC를 연결할 수 있음

#### 증상
- VM은 생성되었지만 Ubuntu 설치 후 네트워크가 잡히지 않거나 인터넷 연결이 되지 않을 수 있음

#### 원인
- 외부 가상 스위치를 만들 때 실제 인터넷이 연결된 물리 NIC가 아닌, 연결이 끊어진 어댑터를 선택하면 VM이 정상적으로 네트워크를 사용하지 못함

#### 해결
`systeminfo`와 네트워크 상태를 확인한 후, 실제 DHCP와 IP가 잡혀 있던 NIC를 외부 스위치에 연결함

```text
Intel(R) Ethernet Connection (17) I219-LM
```

#### 정리
- Hyper-V 외부 가상 스위치는 반드시 현재 실제 네트워크가 연결된 어댑터에 연결해야 함
- 연결이 끊어진 10G 어댑터나 미사용 NIC를 선택하면 VM 네트워크가 정상 동작하지 않음

---

### 5. VM 디스크를 기본 경로가 아닌 SSD에 저장하고 싶었음

#### 증상
- VM 생성 중 가상 하드 디스크가 기본 경로인 시스템 드라이브 쪽으로 생성되려 했음
- 성능과 공간 확보를 위해 SSD 저장 위치로 변경이 필요했음

#### 원인
- Hyper-V 마법사의 기본 경로가 Windows 기본 VM 저장 경로로 설정되어 있었음

#### 해결
VM 생성 과정의 가상 하드 디스크 연결 단계에서 위치를 SSD 경로로 직접 변경함

```text
예시: D:\Hyper-V\app-server\app-server.vhdx
```

또는 Hyper-V 설정에서 기본 저장 경로 자체를 SSD로 변경할 수 있음

#### 정리
- VM 성능과 관리 편의성을 위해 VHDX 파일은 SSD에 두는 것이 유리함
- 특히 DB, MinIO, 로그 등 디스크 I/O가 있는 서버는 저장 위치가 중요함

---

### 6. Ubuntu Pro 활성화 여부 선택 화면이 설치 오류처럼 보였음

#### 증상
- 설치 도중 `Upgrade to Ubuntu Pro` 화면이 나타나 설치가 멈춘 것처럼 보였음

#### 원인
- Ubuntu Server 설치 마지막 단계에서 Ubuntu Pro 가입 여부를 묻는 정상 절차였음

#### 해결
`Skip for now` 선택 후 `Continue`를 눌러 설치를 마무리함

#### 정리
- Ubuntu Pro는 기업용 보안 및 확장 지원 옵션이므로 일반 포트폴리오 서버 구축 단계에서는 필요하지 않음
- 해당 화면은 오류가 아니라 정상 설치 흐름의 일부임

---

### 7. GitHub SSH 키 자동 import 실패

#### 증상
설치 도중 아래와 같은 메시지가 표시됨

```text
Importing keys failed:
ERROR No matching keys found for [gh:<GitHub-username>]
```

#### 원인
- Ubuntu 설치기에서 GitHub 계정의 SSH 공개키를 자동으로 가져오도록 설정했으나, 해당 GitHub 계정에 등록된 공개키가 없었음

#### 해결
- 해당 단계는 무시하고 `Close` 후 설치를 계속 진행함
- SSH 키는 설치 완료 후 직접 `~/.ssh/authorized_keys`에 등록하는 방식으로 처리 가능함

#### 정리
- GitHub 계정에서 SSH 키 자동 import를 사용하려면 GitHub Settings의 `SSH and GPG keys`에 공개키가 먼저 등록되어 있어야 함
- 공개키가 없어도 Ubuntu 설치 자체에는 영향이 없음

---

### 8. 1대 구성과 2대 구성 사이에서 인프라 설계 방향을 다시 정리함

#### 상황
- 처음에는 모든 구성요소를 하나의 VM에 올릴지, 2개의 VM으로 나눌지 결정이 필요했음

#### 판단 기준
- 실사용 목적만 보면 단일 VM도 가능함
- 하지만 포트폴리오에서는 책임 분리와 설계 의도를 설명할 수 있어야 했음

#### 해결
포트폴리오 목적을 고려해 2대 구성으로 방향을 정함

| 서버 | 구성 요소 |
|------|-----------|
| app-server | Nginx, Spring Boot, GitHub Actions Runner |
| data-server | PostgreSQL, Redis, MinIO, Grafana / Prometheus |

#### 정리
- 단일 서버 구성은 빠르게 띄우기에는 좋지만 포트폴리오 설명력이 약할 수 있음
- 앱 서버와 데이터/인프라 서버를 분리하면 책임 분리, 보안, 장애 격리, AWS 유사 구조를 더 잘 설명할 수 있음

---

## 인프라 설계 판단

### 1. Redis, MinIO, Monitoring, CI/CD 도입의 명분 정리

#### 상황
출근 서버 백엔드에 아래 기능이 포함되어 있었음

- 파일 업로드
- 채팅 기능
- CI/CD 필요

#### 판단
기술 도입이 단순 스펙 나열이 아니라 기능 요구사항에 의해 정당화되어야 했음

#### 정리한 방향

| 기술 | 도입 이유 |
|------|-----------|
| Redis | 채팅, 캐시, Pub/Sub, 임시 상태값 저장 |
| MinIO | 파일 업로드, 객체 스토리지 역할, S3 유사 구조 경험 |
| Grafana / Prometheus | 운영 지표 관측, 시스템 상태 확인 |
| GitHub Actions | CI/CD 자동화 |

#### 결론
- 위 기술들은 단순 도입이 아니라 기능 요구사항에 의해 충분히 설명 가능한 선택이었음
- Jenkins는 초기 단계에서 필수는 아니므로 GitHub Actions 기반으로 먼저 진행하는 것이 적절했음

---

# 도메인 연결 및 Swagger 외부 접속 설정

## 1. IP 주소로 직접 Swagger에 접속하던 문제

#### 증상
```text
http://<SERVER-IP>:8080/swagger-ui/index.html
```
처럼 공인 IP와 포트가 그대로 드러나는 형태로 Swagger에 접속해야 했음

#### 원인
- 백엔드 서버가 8080 포트로 직접 외부에 노출되어 있었음
- 도메인 연결 및 리버스 프록시 구성이 없는 상태였음

#### 해결
1. 도메인 구매
2. Cloudflare에 도메인 등록
3. `api.<도메인>` 서브도메인을 서버 공인 IP에 연결
4. 이후 Nginx를 사용해 `80 → 8080` 프록시 구조로 변경

---

## 2. Cloudflare에 잘못된 DNS 레코드가 자동 등록된 문제

#### 증상
Cloudflare에서 도메인 추가 시 아래와 같은 레코드가 자동으로 감지됨

```text
A      <도메인>   75.2.85.42
A      <도메인>   99.83.196.71
CNAME  www        <도메인>
```

하지만 실제 백엔드 서버 IP는 별도였음

#### 원인
- Cloudflare가 기존 DNS 정보를 자동 스캔하면서 현재 프로젝트와 무관한 레코드를 가져왔음

#### 해결
자동 감지된 기존 레코드를 삭제하고, 실제 서버 IP로 직접 DNS 레코드 재등록

```text
A   api   <SERVER-IP>
A   @     <SERVER-IP>
```

---

## 3. Cloudflare 네임서버 적용 전까지 Pending 상태가 유지된 문제

#### 증상
Cloudflare에서 아래와 같은 메시지가 표시됨

```text
Waiting for your registrar to propagate your new nameservers
```

#### 원인
- 도메인 구매 사이트에 등록된 기존 네임서버가 아직 Cloudflare 네임서버로 변경되지 않았거나
- 변경 후 DNS 전파가 완료되지 않은 상태였음

#### 해결
호스팅 도메인 관리 페이지에서 기존 네임서버를 Cloudflare에서 제공한 네임서버 2개로 변경 후 전파 완료까지 대기

```text
olivia.ns.cloudflare.com
seamus.ns.cloudflare.com
```

---

## 4. api.<도메인>:8080은 접속되지만 api.<도메인>는 바로 동작하지 않던 문제

#### 증상
```text
http://api.<도메인>:8080/swagger-ui/index.html  → 접속됨
http://api.<도메인>/swagger-ui/index.html       → 접속 안 되거나 느림
```

#### 원인
- Spring Boot는 8080 포트에서만 직접 서비스 중이었음
- 80 포트에서 요청을 받아 8080으로 넘겨주는 리버스 프록시 구성이 없었음

#### 해결
서버에 Nginx 설치 후 `api.<도메인>` 요청을 `127.0.0.1:8080`으로 전달하도록 설정

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

1. `/etc/nginx/sites-available/<프로젝트명>` 생성
2. `/etc/nginx/sites-enabled/<프로젝트명>`에 심볼릭 링크 추가
3. `nginx -t`로 설정 검사 후 재시작

---

## 5. Nginx 설정 후에도 기본 페이지가 열리던 문제

#### 증상
서버 내부에서 아래 명령 실행 시 Swagger가 아니라 기본 Nginx 응답이 반환됨

```bash
curl -I http://127.0.0.1
```

#### 원인
- Nginx 기본 사이트(`default`)가 여전히 활성화되어 있었음
- `server_name api.<도메인>`에 맞는 설정 블록 대신 기본 블록이 먼저 처리되었음

#### 해결
기본 사이트 비활성화

```bash
sudo rm /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

---

## 6. Nginx는 정상인데 루트 경로(/)에서 403이 발생한 문제

#### 증상
```bash
curl -I -H "Host: api.<도메인>" http://127.0.0.1
```
실행 시 `HTTP/1.1 403` 반환

#### 원인
- 루트 경로(`/`)는 애플리케이션에서 허용되지 않은 경로였음
- Nginx 문제라기보다 Spring Security 또는 애플리케이션 라우팅 구조상 `/`가 보호 또는 미구현 상태였음

#### 해결
루트 경로 대신 실제 Swagger 경로로 재테스트

```bash
curl -I -H "Host: api.<도메인>" http://127.0.0.1/swagger-ui/index.html
```

Swagger 경로에서 `200 OK` 응답 확인 → `Nginx → Spring` 프록시 정상 동작 확인

---

## 7. Cloudflare를 사용할 때 521 Web server is down이 발생한 문제

#### 증상
외부 브라우저에서 접속 시 Cloudflare가 다음 에러를 반환함

```text
521 Web server is down
```

#### 원인
- Cloudflare는 접근 가능했지만 `Cloudflare → 원본 서버(Origin)` 연결이 실패함
- 프록시, 공유기 포트포워딩, 서버 접근 가능 여부를 함께 확인할 필요가 있었음

#### 해결
UFW 방화벽 상태 및 Nginx 리슨 포트 확인

```bash
sudo ufw status
sudo ss -ltnp | grep ':80'
```

Cloudflare `api` 레코드를 일시적으로 `Proxied → DNS only`로 변경하여 Cloudflare 프록시 우회
→ 원본 서버 자체와 Nginx 설정은 정상임을 분리 확인

---

## 8. 서버 내부에서는 정상인데 외부 접속 시 Swagger 정적 리소스 로딩이 매우 느린 문제

#### 증상
Swagger `index.html`은 열리지만 아래 파일들이 오래 대기하거나 로딩되지 않음

```text
swagger-ui.css
swagger-ui-bundle.js
swagger-ui-standalone-preset.js
swagger-initializer.js
```

브라우저에서는 빈 화면처럼 보이거나 매우 느리게 표시됨

#### 원인
- 8080 직결 환경에서는 비교적 빠르게 동작했지만 `80 → Nginx → 8080` 구조로 변경하면서 외부 정적 리소스 로딩 체감이 느려짐
- Cloudflare, 포트포워딩, 외부망, 브라우저의 다중 정적 파일 요청이 함께 얽히면서 체감 지연이 커졌음
- 애플리케이션 코드나 Swagger 설정 자체보다는 네트워크 경로와 프록시 환경 영향이 더 컸음

#### 해결
- `api` 레코드를 `DNS only` 상태로 두고 원인 분리
- 내부 `curl` 테스트로 정적 리소스가 애플리케이션에서 정상 제공되는지 확인
- 8080 외부 포트포워딩 제거 방향으로 정리
- 외부 노출 포트를 `80`, `443` 중심으로 단순화

---

## 9. HTTP 포트에서 HTTPS 요청이 들어와 Tomcat이 요청 헤더를 파싱하지 못한 문제

#### 증상
애플리케이션 로그에 아래와 같은 에러가 출력됨

```text
Invalid character found in method name [0x16 0x03 0x01 ...]
HTTP method names must be tokens
```

#### 원인
- HTTP만 받는 8080 포트에 HTTPS(TLS) 요청이 직접 들어왔음
- 브라우저, 외부 스캐너, 혹은 잘못된 접근 경로로 인해 HTTPS 패킷이 HTTP 포트에 전달됨

#### 해결
- 원인 자체는 애플리케이션 장애가 아니라 프로토콜 불일치임을 확인
- 외부에서 8080으로 직접 접근하지 않도록 구조 정리
- 공유기 포트포워딩에서 `8080 → 8080` 규칙 제거
- 최종적으로 외부는 `80/443`, 내부 앱은 `8080`만 사용하도록 정리

---

## 10. Nginx에 443 SSL 설정을 넣었지만 인증서 파일이 없어 실패한 문제

#### 증상
`nginx -t` 수행 시 아래 에러 발생

```text
cannot load certificate "/etc/ssl/certs/cloudflare-origin.pem"
No such file or directory
```

#### 원인
- Nginx 설정 파일에 `ssl_certificate`, `ssl_certificate_key`를 먼저 추가했지만
- 실제 Cloudflare Origin 인증서 파일이 서버에 아직 생성되지 않았음

#### 해결
- 443 서버 블록을 우선 제거하여 HTTP 구성 복구
- 80 포트 리버스 프록시만 남긴 상태에서 정상 동작 확인
- 이후 HTTPS 적용 시 Cloudflare Origin Certificate를 발급받아 아래 경로에 저장하는 방식으로 진행 예정

```text
/etc/ssl/certs/cloudflare-origin.pem
/etc/ssl/private/cloudflare-origin.key
```

---

## 11. SwaggerConfig와 SecurityConfig가 문제 원인처럼 보였지만 실제 핵심 원인은 아니었던 문제

#### 증상
Swagger 화면이 느리게 로딩되거나 일부 리소스가 늦게 표시되어 `SwaggerConfig`, `SecurityConfig` 문제 가능성을 의심함

#### 원인
- `SwaggerConfig`는 OpenAPI 메타데이터와 JWT 인증 스키마만 정의하고 있었음
- `SecurityConfig`도 `/swagger-ui/**`, `/v3/api-docs/**`를 `permitAll()`로 허용하고 있어 큰 문제는 없었음
- 실제 병목은 애플리케이션 설정이 아니라 외부 접근 경로, 프록시, DNS, 브라우저 리소스 요청 흐름에 더 가까웠음

#### 해결
- Swagger 허용 경로가 이미 열려 있음을 확인
- 코드보다는 인프라 경로 문제에 집중하여 진단 진행
- 필요 시 아래 경로까지 추가 허용 가능하도록 검토

```text
"/swagger-ui.html"
```

---

# GitHub Actions 기반 CD 파이프라인 구축

## 1. SERVER_HOST에 도메인을 넣어 SSH 접속이 실패한 문제

#### 증상
GitHub Actions에서 `scp-action` 실행 시 아래와 같이 타임아웃 발생

```text
error copy file to dest: ..., error message: dial tcp <CLOUDFLARE-IP>:22: i/o timeout
```

#### 원인
- `SERVER_HOST`에 도메인을 넣었음
- 해당 도메인은 Cloudflare 프록시를 거치고 있어 SSH 대상 호스트로 적절하지 않았음
- SSH/SCP는 웹 도메인이 아니라 원본 서버의 공인 IP로 직접 붙어야 함

#### 해결
GitHub Secrets의 `SERVER_HOST` 값을 도메인이 아닌 공인 IP로 변경

```text
SERVER_HOST=<SERVER-IP>
```

---

## 2. GitHub Actions에서 SSH 개인키를 인식하지 못한 문제

#### 증상
배포 중 아래 로그 발생

```text
ssh.ParsePrivateKey: ssh: no key found
```

#### 원인
- `SERVER_SSH_KEY`에 올바른 SSH 개인키가 들어가지 않았음
- 공개키(`.pub`)를 넣었거나, 줄바꿈/형식이 깨진 상태였을 가능성이 있었음

#### 해결
1. 배포 전용 SSH 키 생성
2. 공개키는 서버의 `authorized_keys`에 등록
3. 개인키 전체를 GitHub Secret `SERVER_SSH_KEY`에 등록

등록 형식:

```text
-----BEGIN OPENSSH PRIVATE KEY-----
...
-----END OPENSSH PRIVATE KEY-----
```

---

## 3. 서버에 공개키가 등록되지 않아 SSH 인증이 실패한 문제

#### 증상
SSH 배포 준비 중 서버의 `~/.ssh/authorized_keys` 파일이 비어 있었음

```bash
ls -la ~/.ssh
cat ~/.ssh/authorized_keys
```

```text
-rw------- 1 <username> <username> 0 ... authorized_keys
```

#### 원인
- GitHub Actions가 서버에 접속할 수 있도록 허용할 공개키가 서버에 등록되지 않았음

#### 해결
배포용 공개키를 직접 `authorized_keys`에 추가

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
cat github_actions_deploy.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

---

## 4. GitHub Actions가 서버의 22번 포트에 접속하지 못한 문제

#### 증상
배포 시 아래와 같은 타임아웃 발생

```text
dial tcp <SERVER-IP>:22: i/o timeout
```

외부 PC에서 확인 시:

```powershell
Test-NetConnection <SERVER-IP> -Port 22
```

```text
TcpTestSucceeded : False
```

#### 원인
- 서버 내부의 `sshd`는 정상 동작하고 있었지만 외부 공인망에서 22 포트 접근이 불가능했음
- 공유기/망 환경상 외부 22 포트가 막혀 있었음

#### 해결
공유기에서 외부 `2222` 포트를 내부 `22` 포트로 포트포워딩 후 GitHub Secrets의 `SERVER_PORT`를 `2222`로 변경

```text
외부 2222 -> 내부 <PRIVATE-IP>:22
SERVER_PORT=2222
```

외부 테스트 결과:

```text
Test-NetConnection <SERVER-IP> -Port 2222
TcpTestSucceeded : True
```

---

## 5. scp-action으로 업로드한 JAR 경로가 예상과 달랐던 문제

#### 증상
원격 배포 단계에서 아래 에러 발생

```text
cp: cannot stat '/home/<username>/app/attendance-0.0.1-SNAPSHOT.jar': No such file or directory
```

#### 원인
- GitHub Actions에서 `source: "build/libs/attendance-0.0.1-SNAPSHOT.jar"`로 업로드했을 때
- 파일이 `/home/<username>/app/` 바로 아래가 아니라 하위 디렉터리 구조까지 포함되어 업로드됨

실제 업로드 경로:

```text
/home/<username>/app/build/libs/attendance-0.0.1-SNAPSHOT.jar
```

#### 해결
원격 배포 스크립트에서 복사 경로를 실제 업로드 위치로 수정

```bash
cp /home/<username>/app/build/libs/attendance-0.0.1-SNAPSHOT.jar /home/<username>/app/app.jar
```

디버깅을 위해 배포 시점에 실제 파일 구조를 출력하여 확인

```bash
ls -R /home/<username>/app
```

---

## 6. GitHub Actions에서 sudo systemctl restart 실행 시 비밀번호를 요구한 문제

#### 증상
배포 마지막 단계에서 아래 에러 발생

```text
sudo: a terminal is required to read the password
sudo: a password is required
```

#### 원인
- 서버 유저가 `sudo systemctl restart <서비스명>` 실행 시 비밀번호를 요구하는 상태였음
- GitHub Actions는 비대화형 환경이라 sudo 비밀번호 입력이 불가능함

#### 해결
해당 유저가 `systemctl` 명령을 비밀번호 없이 실행할 수 있도록 sudoers 설정 추가

```bash
sudo visudo -f /etc/sudoers.d/<username>
```

내용:

```text
<username> ALL=(ALL) NOPASSWD: /usr/bin/systemctl
```

권한 설정 및 적용 확인:

```bash
sudo chmod 440 /etc/sudoers.d/<username>
sudo -l -U <username>
```

```text
(ALL) NOPASSWD: /usr/bin/systemctl
```

이후 아래 명령이 비밀번호 없이 정상 동작함을 확인

```bash
sudo systemctl restart <서비스명>
sudo systemctl status <서비스명> --no-pager
```

---

## 7. /etc/sudoers 본문에 직접 넣은 규칙이 중복되어 관리가 복잡해진 문제

#### 증상
`/etc/sudoers` 하단에 동일한 규칙이 중복으로 들어감

```text
<username> ALL=NOPASSWD: /usr/bin/systemctl restart <서비스명>, ...
<username> ALL=NOPASSWD: /usr/bin/systemctl restart <서비스명>, ...
```

#### 원인
- 초기 디버깅 과정에서 `/etc/sudoers` 본문에 직접 규칙을 여러 번 추가했음

#### 해결
사용자별 커스텀 sudo 규칙은 `/etc/sudoers.d/<username>` 파일로 분리하고, `/etc/sudoers` 본문에는 직접 규칙을 남기지 않고 정리

최종적으로 아래 파일만 사용하도록 구성:

```text
/etc/sudoers.d/<username>
```

```text
<username> ALL=(ALL) NOPASSWD: /usr/bin/systemctl
```

---

## 8. main 머지 시 자동 배포를 원했지만, PR 이벤트와 배포 트리거를 혼동한 문제

#### 증상
PR 생성 시점에도 배포가 실행될 수 있는지 혼동이 있었음

#### 원인
- `pull_request` 이벤트와 `push` 이벤트의 차이를 명확히 분리하지 않았음
- "PR을 열 때"와 "PR을 merge해서 main에 반영될 때"의 차이를 혼동함

#### 해결
배포 워크플로는 `main` 브랜치에 대한 `push` 이벤트만 사용하도록 구성

```yaml
on:
  push:
    branches: [ main ]
```

- PR 생성 시에는 배포되지 않음
- `main`에 최종 merge되어 실제 push가 발생할 때만 배포됨

---

## 9. main 브랜치 보호 규칙에서 Required status checks가 바로 보이지 않던 문제

#### 증상
Ruleset 설정에서 `Require status checks to pass`를 켰지만 아래 상태가 표시됨

```text
No required checks
No checks have been added
```

#### 원인
- GitHub가 아직 해당 브랜치 기준으로 인식할 수 있는 status check 목록을 표시하지 않은 상태였음
- PR에서 CI가 한 번 돌았더라도 ruleset 화면에서 바로 selectable하게 뜨지 않는 경우가 있었음

#### 해결
- CI 워크플로를 실행시켜 최근 성공 이력을 만든 뒤 다시 확인
- 이후 `CI / test` 형태의 체크를 required check로 연결할 수 있도록 정리

---

# ATTENDANCE 도메인

## 1. V5 SQL - BIGINT만 선언하면 PostgreSQL에서 자동 증가가 되지 않음

**증상**
```
attendance_id가 null로 저장되거나 PK 자동 생성이 안 됨
```

**원인**
`BIGINT PRIMARY KEY`로만 선언하면 PostgreSQL에서 자동 증가가 되지 않음.
MySQL에서는 `AUTO_INCREMENT`를 별도로 붙이지만, PostgreSQL에서는 `BIGSERIAL`을 사용해야 함.

**해결**
```sql
-- Before
attendance_id BIGINT PRIMARY KEY

-- After
attendance_id BIGSERIAL PRIMARY KEY
```

---

## 2. V5 SQL - 외래키 참조 컬럼 오류

**증상**
```
ERROR: column "id" referenced in foreign key constraint does not exist
```

**원인**
`member` 테이블의 PK 컬럼명은 `member_id`인데 `REFERENCES member(id)`로 잘못 참조함.

**해결**
```sql
-- Before
member_id BIGINT NOT NULL REFERENCES member(id)

-- After
member_id BIGINT NOT NULL REFERENCES member(member_id)
```

---

## 3. 동일 경로에 두 메서드가 매핑되어 contextLoads() 실패

**증상**
```
AttendanceApplicationTests > contextLoads() FAILED
    Caused by: java.lang.IllegalStateException at AbstractHandlerMethodMapping.java:677
```

**원인**
ATT-006 구현 시 `getAttendancesByFilter()` 메서드를 추가하면서 기존 `getAttendancesByDate()` 메서드를 삭제하지 않아 `GET /api/v1/attendances`에 두 메서드가 중복 매핑됨.

**해결**
`AttendanceController`에서 기존 `getAttendancesByDate()` 메서드 삭제.
`AttendanceService`에서도 동일하게 삭제.

---

## 4. CI 환경(UTC)에서 LocalDate.now() 가 KST 서비스와 하루 어긋나 테스트 6개 실패 (#184)

**증상**
GitHub Actions(Ubuntu/UTC) 에서 KST 자정 직후 시각(KST 00:00~09:00, UTC 기준 전날) 에 빌드가 돌면 `AttendanceServiceTest` 의 6개 테스트가 동시에 실패.
```
AttendanceServiceTest > 야간 근무 다음날 새벽 퇴근 시 전날 attendance로 퇴근 FAILED
AttendanceServiceTest > 오늘 출근하는 모든 사람 조회 FAILED
AttendanceServiceTest > 팀 필터로 출퇴근 기록 조회 FAILED
AttendanceServiceTest > 오늘 출근 기록 초기화 성공 FAILED
AttendanceServiceTest > 팀 필터 없이 전체 출퇴근 기록 조회 FAILED
AttendanceServiceTest > 자정 자동 퇴근 처리 FAILED
```
로컬(KST) 머신에서는 모두 통과해서 재현이 어려웠음.

**원인**
프로덕션 코드(`AttendanceService`) 는 `LocalDateTime.now(KST)`, `LocalDate.now(KST)` 로 KST 기준 날짜를 계산하는데, 테스트(`AttendanceServiceTest`) 와 컨트롤러(`AttendanceController.resetAttendance`) 는 zone 인자 없는 `LocalDate.now()` / `LocalDateTime.now().toLocalDate()` 를 사용.
KST 자정 ~ UTC 전날 시각대에 빌드/요청이 들어오면 두 값의 날짜가 하루 어긋나 `attendance.workDate` 와 조회 키가 매치되지 않음. 운영 서버(UTC) 에서는 같은 시간대에 `DELETE /api/v1/attendances/me` 가 `ATTENDANCE_NOT_FOUND` 로 실패.

**해결**
테스트 7군데와 컨트롤러를 KST 기준으로 통일.
```java
// Before
LocalDate date = LocalDate.now();

// After
private static final ZoneId KST = ZoneId.of("Asia/Seoul");
LocalDate date = LocalDate.now(KST);
```
`AttendanceController.resetAttendance` 의 기본 날짜도 동일하게 `LocalDate.now(KST)` 로 교체. `TZ=UTC -Duser.timezone=UTC ./gradlew test` 로 UTC 환경을 강제하여 회귀 검증.

전 도메인의 `LocalDateTime.now()` → `Clock` 빈 주입으로 일괄 정리하는 작업은 범위가 커서 별도 이슈로 분리.

---

## 5. 출근 내역 초기화 시 연결된 attendance_exception FK 제약으로 500 발생 (#187)

**증상**
프론트가 정확한 `workDate` 를 query param 으로 보내도 응답이 500 INTERNAL_ERROR.
```
DELETE /api/v1/attendances/me?date=2026-04-22
→ 500
```

**원인**
`AttendanceService.resetAttendance` 가 attendance 를 바로 삭제하는데, `attendance_exception.attendance_id` 가 `attendance(attendance_id)` 를 FK 로 참조 중. `AttendanceExceptionService.generateMissingExceptions` 가 출근 기록이 존재하는 날짜에 대해 `MISSED_CHECK_OUT` / `LATE` 같은 예외 row 를 자동 생성하기 때문에 reset 대상 attendance 에는 거의 항상 연결 예외가 존재함.
결과적으로 `attendanceRepository.delete(attendance)` 호출 시 `DataIntegrityViolationException` 이 발생하고, `GlobalExceptionHandler` 의 generic `Exception` 핸들러가 잡아 500 INTERNAL_ERROR 로 떨어짐.

**해결**
"옵션 A: cascade 삭제" 정책 채택 — attendance 삭제 전에 연결 예외부터 같이 제거.
```java
public void resetAttendance(Long memberId, LocalDate today) {
    Attendance attendance = attendanceRepository.findByMemberIdAndWorkDate(memberId, today)
            .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_NOT_FOUND));
    attendanceExceptionRepository.deleteAllByAttendanceId(attendance.getId());
    attendanceRepository.delete(attendance);
}
```
`AttendanceExceptionRepository.deleteAllByAttendanceId(Long)` 추가 (Spring Data 메서드 이름 규칙으로 자동 구현).

옵션 B(예외 유지 + FK null) 를 택하지 않은 이유:
- `uk_member_workdate_type` 유니크 제약 때문에 다음 `generateMissingExceptions` 사이클에 새 예외가 만들어질 때 충돌 위험
- reset 은 본인 기록만 대상이라 감사 이력 보존 가치가 낮음
- 예외는 `generateMissingExceptions` 가 idempotent 하게 재생성하는 구조라 cascade 로 지워도 자연 복구됨

회귀 테스트 추가: 연결된 `attendance_exception` 이 있어도 초기화 후 attendance / exception 모두 비어 있는지 검증.

---

# TASK 도메인

## 1. TaskSpringDataRepository에 JpaRepository 메서드 중복 선언

**증상**
컴파일은 되지만 불필요한 오버라이딩으로 코드가 지저분해짐.

**원인**
`JpaRepository<Task, Long>`을 상속하면 `save()`, `findById()`, `deleteById()`가 이미 제공되는데 인터페이스에 동일 메서드를 다시 선언함.

**해결**
`TaskSpringDataRepository`에서 중복 선언 제거. `JpaRepository` 상속만으로 충분.
```java
public interface TaskSpringDataRepository extends JpaRepository<Task, Long> {
}
```

---

## 2. Task 다중 멤버 추가 후 기존 테스트 컴파일 오류

**증상**
```
constructor TaskCreateRequest in record TaskCreateRequest cannot be applied to given types;
required: String,LocalDate,LocalTime,TaskPriority,Long,boolean,List<Long>
found:    String,LocalDate,LocalTime,TaskPriority,<null>,boolean
```

**원인**
TASK-008 다중 멤버 기능 추가로 `TaskCreateRequest`, `TaskUpdateRequest` record에 `memberIds` 파라미터가 추가됐는데, 기존 테스트 코드는 이전 생성자 시그니처를 그대로 사용하고 있었음.

**해결**
기존 테스트의 `TaskCreateRequest` 호출에 `null` 추가, `TaskUpdateRequest` 호출에도 `null` 추가.
```java
// Before
new TaskCreateRequest("제목", LocalDate.now(), null, TaskPriority.HIGH, null, false)

// After
new TaskCreateRequest("제목", LocalDate.now(), null, TaskPriority.HIGH, null, false, null)
```

---

## 3. TaskTest에서 QTask 정적 import 충돌

**증상**
```
import static com.yanus.attendance.task.domain.QTask.task 로 인해
로컬 변수 task와 정적 필드 task가 충돌하여 컴파일 오류 발생
```

**원인**
`QTask.task` 정적 필드를 import했는데 테스트 내부에서 `Task task = Task.createPersonal(...)` 형태의 로컬 변수도 `task`로 선언되어 네이밍 충돌 발생.

**해결**
`TaskTest.java`에서 `import static com.yanus.attendance.task.domain.QTask.task` 제거.
도메인 단위 테스트에서는 QueryDSL Q클래스를 사용할 필요가 없음.

---

## 4. Task 도메인 테스트에서 createPersonal/createTeam 파라미터 누락

**증상**
```
method createPersonal in class Task cannot be applied to given types;
required: Member,String,LocalDate,LocalTime,TaskPriority,List<Member>
found:    Member,String,LocalDate,LocalTime,TaskPriority
```

**원인**
TASK-008로 `createPersonal`, `createTeam`에 `List<Member> members` 파라미터가 추가됐는데 기존 도메인 테스트는 파라미터 없이 호출하고 있었음.

**해결**
도메인 테스트의 모든 `createPersonal`, `createTeam` 호출에 마지막 인자로 `null` 추가.

---

## 5. FakeMemberRepository에 findAllByIds 미구현

**증상**
```
FakeMemberRepository is not abstract and does not override abstract method findAllByIds(List<Long>) in MemberRepository
```

**원인**
TASK-008 구현으로 `MemberRepository` 인터페이스에 `findAllByIds(List<Long>)` 메서드가 추가됐는데 `FakeMemberRepository`에 구현하지 않음.

**해결**
`FakeMemberRepository`에 메서드 추가.
```java
@Override
public List<Member> findAllByIds(List<Long> ids) {
    return store.values().stream()
            .filter(m -> ids.contains(m.getId()))
            .toList();
}
```

---

## 6. TaskServiceTest에 java.util.List import 누락

**증상**
```
cannot find symbol
symbol: variable List
location: class TaskServiceTest
```

**원인**
`List.of(...)` 사용 시 `java.util.List` import가 누락됨.

**해결**
```java
import java.util.List;
```

---

# CALENDAR 도메인

## 1. 도메인 테스트 CRUD 범위 혼동

**증상**
도메인 테스트에 Create/Read/Update/Delete를 모두 넣으려 했으나 일부는 도메인 로직이 없는 경우였음.

**원인**
도메인 테스트는 엔티티 비즈니스 로직(유효성 검증, 상태 변경)만 테스트해야 하는데, Read/Delete는 Repository 레벨 동작이라 도메인 테스트 대상이 아님.

**해결**
도메인 테스트 = 엔티티 동작 (create 필드 검증, update 필드 변경, 유효성 검증 예외)
CRUD 전체 = 서비스 테스트에서 커버.
도메인 테스트에 `update_with_invalid_end_time` 추가로 마무리.

---

# DRIVE 도메인

## 1. MinIO 의존성 로딩 실패 (Spring Boot 3 의존성 충돌)

**증상**
```
MinIO Bean이 생성되지 않거나 의존성 충돌로 빌드 실패
```

**원인**
MinIO 8.5.x는 내부적으로 `okhttp3`, `guava`를 사용하는데 Spring Boot 3 환경에서 버전 충돌이 발생할 수 있음.

**해결**
`build.gradle`에서 충돌 의존성 제외 후 명시적 버전 지정.
```groovy
implementation('io.minio:minio:8.5.7') {
    exclude group: 'com.google.guava', module: 'guava'
    exclude group: 'com.squareup.okhttp3', module: 'okhttp'
}
implementation 'com.google.guava:guava:32.1.3-jre'
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
```

---

## 2. CI에서 MinIO 환경변수 미설정으로 contextLoads() 실패

**증상**
```
AttendanceApplicationTests > contextLoads() FAILED
    Caused by: org.springframework.util.PlaceholderResolutionException
```

**원인**
DRIVE 도메인 추가로 `application.properties`에 `${MINIO_ENDPOINT}` 등 MinIO 관련 플레이스홀더가 추가됐는데 CI 환경(GitHub Actions)에 해당 환경변수가 설정되지 않아 Spring Context 로딩 실패.

**해결**
`src/test/resources/application.properties`에 테스트용 기본값 추가.
```properties
minio.endpoint=http://localhost:9000
minio.access-key=test
minio.secret-key=testpassword
minio.bucket=test-bucket
```
테스트 환경은 실제 MinIO에 연결하지 않으므로 가짜 값으로도 Context 로딩 가능.

---

## 3. DriveFile 엔티티에 bucket 컬럼 불필요

**증상**
설계 초기에 `drive_file` 테이블에 `bucket` 컬럼을 포함했으나 실제로는 필요 없음.

**원인**
버킷명은 `application.properties`의 `minio.bucket`으로 중앙 관리되기 때문에 파일별로 DB에 저장할 필요가 없음.

**해결**
`DriveFile` 엔티티와 V11 마이그레이션에서 `bucket` 컬럼 제거.
`DriveFile.create()` 파라미터에서도 `bucket` 제거.

---

# WORK SCHEDULE 도메인

## 1. 근무 일정 삭제 시 존재하지 않는 요일 예외 처리 누락

**증상**
존재하지 않는 요일로 `DELETE /api/v1/work-schedules/{dayOfWeek}` 요청 시 500 에러 반환.

**원인**
`deleteByMemberIdAndDayOfWeek()` 호출 전에 존재 여부 확인 로직이 없었음.

**해결**
삭제 전 `findByMemberIdAndDayOfWeek()`로 존재 여부 확인 후 없으면 `WORK_SCHEDULE_NOT_FOUND` 예외 발생.
```java
workScheduleRepository.findByMemberIdAndDayOfWeek(memberId, dayOfWeek)
        .orElseThrow(() -> new BusinessException(ErrorCode.WORK_SCHEDULE_NOT_FOUND));
workScheduleRepository.deleteByMemberIdAndDayOfWeek(memberId, dayOfWeek);
```

---

## 2. 팀/전체 근무 일정 조회 응답 구조 설계

**증상**
멤버별 근무 일정을 조회할 때 단순 리스트로 반환하면 같은 멤버의 요일별 일정이 각각 분리되어 응답됨.

**원인**
`WorkSchedule` 엔티티는 요일(day_of_week)별로 한 행씩 저장되는 구조이기 때문.

**해결**
`MemberWorkScheduleResponse` DTO를 추가하여 멤버별로 그룹핑 후 반환.
```java
schedules.stream()
    .collect(Collectors.groupingBy(WorkSchedule::getMember))
    .entrySet().stream()
    .map(entry -> new MemberWorkScheduleResponse(...))
    .toList();
```

---

# AUTH 도메인

## 1. 비활성화(INACTIVE) 멤버 로그인 차단 누락 (#88)

**증상**
관리자가 멤버를 `INACTIVE` 처리해도 해당 멤버가 정상적으로 로그인 가능.

**원인**
`AuthService.login()`에서 비밀번호 검증만 하고 `status` 체크 로직이 없었음.

**해결**
`Member.isInactive()` 메서드 추가 후 로그인 시 상태 체크.
```java
if (member.isInactive()) {
    throw new BusinessException(ErrorCode.MEMBER_INACTIVE);
}
```
`ErrorCode.MEMBER_INACTIVE(HttpStatus.FORBIDDEN)` 추가.

---

# TEAM 도메인

## 1. TeamName enum → String 변환 시 @Enumerated 어노테이션 잔존

**증상**
```
Caused by: org.hibernate.AnnotationException
  at BasicValueBinder.java:814
```
`contextLoads()` 실패, 서버 502 에러 발생.

**원인**
`Team.name` 타입을 `TeamName` enum → `String`으로 변경했으나 `@Enumerated(EnumType.STRING)` 어노테이션을 제거하지 않음.
Hibernate는 `String` 타입 필드에 `@Enumerated`가 붙으면 AnnotationException 발생.

**해결**
`Team.java`에서 `@Enumerated(EnumType.STRING)` 및 관련 import 제거.
```java
// Before
@Enumerated(EnumType.STRING)
@Column(name = "name", nullable = false, unique = true, length = 50)
private String name;

// After
@Column(name = "name", nullable = false, unique = true, length = 50)
private String name;
```

---

## 2. V12 마이그레이션 FK 제약 조건 위반으로 서버 502 에러

**증상**
```
ERROR: update or delete on table "team" violates foreign key constraint
Detail: Key (team_id)=(1) is still referenced from table "member"
Location: db/migration/V12__remove_default_teams.sql
```
배포 후 서버 502 Bad Gateway 발생.

**원인**
`V12__remove_default_teams.sql`에서 `DELETE FROM team` 실행 시 `member` 테이블이 `team_id`를 FK로 참조 중인 팀이 존재하여 삭제 불가.
CI 환경에서는 데이터가 없어 통과했으나 운영 환경에서는 실제 멤버 데이터가 있었음.

**해결**
멤버가 소속된 팀은 제외하고 참조 없는 팀만 삭제하도록 SQL 수정.
```sql
DELETE FROM team
WHERE team_id NOT IN (
    SELECT DISTINCT team_id FROM member WHERE team_id IS NOT NULL
);
```
운영 DB에서 Flyway 실패 기록 제거 후 재배포.
```bash
sudo -u postgres psql -d yanus -c "DELETE FROM flyway_schema_history WHERE version = '12';"
```

---

# DRIVE 도메인 (추가)

## 4. DriveFileController GET 중복 매핑으로 contextLoads() 실패

**증상**
```
Caused by: java.lang.IllegalStateException
  at AbstractHandlerMethodMapping.java:677
```

**원인**
`getAllFiles()` 메서드 추가 시 기존 `getMyFiles()`를 제거하지 않아 `GET /api/v1/drive`에 `@GetMapping`이 두 개 존재.

**해결**
`getMyFiles()` 메서드 제거 후 `getAllFiles()` 하나만 유지.

---
